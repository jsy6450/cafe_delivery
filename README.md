# cafe_delivery
# Table of contents

- [음료배달](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [API Gateway](#API-GATEWAY)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
    - [CQRS / Meterialized View](#CQRS--Meterialized-View)
  - [운영](#운영)
    - [Liveness / Readiness 설정](#Liveness--Readiness-설정)
    - [CI/CD 설정](#cicd-설정)
    - [셀프힐링](#셀프힐링)
    - [무정지 재배포](#무정지-재배포)
    - [ConfigMap / Secret](#ConfigMap--Secret)

# 음료배달 서비스 시나리오

기능적 요구사항
- 고객이 주문 취소를 하게 되면, 주문정보는 삭제되나, 배송팀에서는 사후 활용을 위해 취소장부를 별도 저장한다.
- 배송 서비스는 게이트웨이를 통해 관리된다.

비기능요건
- 주문 취소는 반드시 배송취소가 전제되어야 한다.

추가기능요건
- 배송센터는 배송페이지를 통해, 주문내역, 배송내역, 취소내역을 열람할 수 있다.


# 분석설계

1. Event Storming 모델
![delivery](https://user-images.githubusercontent.com/31643538/108974871-3be06d80-76c9-11eb-9367-20f5ebf10f85.jpg)
1. 헥사고날 아키텍처 다이어그램 도출
![hexa](https://user-images.githubusercontent.com/31643538/109002524-af927280-76e9-11eb-9695-66788ba41206.png)


- REST API 의 테스트
```
# order 서비스의 주문처리
root@seige-74d7df4cd9-7sckv:/# http http://order:8080/orders phoneNumber="01082947794" productName="coffee" qty=3 amt=6000 
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Wed, 24 Feb 2021 10:27:20 GMT
Location: http://order:8080/orders/8
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/8"
        },
        "self": {
            "href": "http://order:8080/orders/8"
        }
    },
    "amt": 6000,
    "createTime": "2021-02-24T10:27:20.732+0000",
    "phoneNumber": "01082947794",
    "productName": "coffee",
    "qty": 3,
    "status": "Ordered"
}

# drink 서비스의 이벤트 발생
root@seige-74d7df4cd9-7sckv:/# http PATCH http://drink:8080/drinks/2 status="Made"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Wed, 24 Feb 2021 10:29:24 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/2"
        },
        "self": {
            "href": "http://drink:8080/drinks/2"
        }
    },
    "createTime": "2021-02-24T10:27:20.846+0000",
    "orderId": 8,
    "phoneNumber": "01082947794",
    "productName": "coffee",
    "qty": 3,
    "status": "Made"
}

# 배달 서비스 확인
root@seige-74d7df4cd9-7sckv:/# http http://delivery:8080/deliveries
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Wed, 24 Feb 2021 10:30:16 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "deliveries": [
            {
                "_links": {
                    "delivery": {
                        "href": "http://delivery:8080/deliveries/9"
                    },
                    "self": {
                        "href": "http://delivery:8080/deliveries/9"
                    }
                },
                "orderId": 8,
                "status": "Delivery Started"
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://delivery:8080/profile/deliveries"
        },
        "self": {
            "href": "http://delivery:8080/deliveries{?page,size,sort}",
            "templated": true
        }
    },
    "page": {
        "number": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    }
}

#주문취소
root@seige-74d7df4cd9-7sckv:/# http DELETE http://order:8080/orders/8
HTTP/1.1 204 
Date: Wed, 24 Feb 2021 10:53:35 GMT

#배송 서비스 내 취소장부 별도 저장
root@seige-74d7df4cd9-7sckv:/# http http://delivery:8080/cancellations
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Wed, 24 Feb 2021 11:05:01 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "cancellations": [
            {
                "_links": {
                    "cancellation": {
                        "href": "http://delivery:8080/cancellations/10"
                    },
                    "self": {
                        "href": "http://delivery:8080/cancellations/10"
                    }
                },
                "orderId": 8,
                "status": "Delivery Cancelled"
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://delivery:8080/profile/cancellations"
        },
        "self": {
            "href": "http://delivery:8080/cancellations{?page,size,sort}",
            "templated": true
        }
    },
    "page": {
        "number": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    }
}

#배송센터 뷰로 배송내역 조회 가능
root@seige-74d7df4cd9-7sckv:/# http http://deliverycenter:8080/deliverypages
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Wed, 24 Feb 2021 10:53:38 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "deliverypages": [
            {
                "_links": {
                    "deliverypage": {
                        "href": "http://deliverycenter:8080/deliverypages/2"
                    },
                    "self": {
                        "href": "http://deliverycenter:8080/deliverypages/2"
                    }
                },
                "amt": 6000,
                "deliveryId": null,
                "orderId": 8,
                "status": "Delivery Cancelled"
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://deliverycenter:8080/profile/deliverypages"
        },
        "search": {
            "href": "http://deliverycenter:8080/deliverypages/search"
        },
        "self": {
            "href": "http://deliverycenter:8080/deliverypages"
        }
    }
}
```

## API Gateway
API Gateway를 통하여 동일주소로 진입하여 각 마이크로서비스를 접근할 수 있다.
외부에서 접근을 위하여 Gateway의 Service는 LoadBalancer Type으로 설정한다.

```
# application.yml
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/** 
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/payments/** 
        - id: drink
          uri: http://drink:8080
          predicates:
            - Path=/drinks/**,/orderinfos/**
        - id: delivery
          uri: http://delivery:8080
          predicates:
            - Path= /deliveries/**,/cancellations/**
        - id: deliverycenter
          uri: http://deliverycenter:8080
          predicates:
            - Path= /deliverypages/**

# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: gateway 
  labels:
    app: gateway 
spec:
  type: LoadBalancer
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: gateway 
```
*** 외부 접근 호출 capture(배송페이지 열람) ***
![gateway](https://user-images.githubusercontent.com/31643538/108998745-b1a60280-76e4-11eb-834b-2db1b8fd7496.png)


## 폴리글랏 퍼시스턴스

배송센터(deliverycenter)는 hsqldb를 사용하여 정상 작동을 확인하였다.
```
#deliverycenter > pom.xml

<dependencies>
:
    <!-- HSQL -->
    <dependency>
	<groupId>org.hsqldb</groupId>
	<artifactId>hsqldb</artifactId>
	<version>2.4.0</version>
	<scope>runtime</scope>
    </dependency>
:
</dependencies>

```

## 동기식 호출 

분석단계에서의 조건 중 하나로 주문취소(order)->취소장부(delivery)간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
호출 프로토콜은 FeignClient 를 이용하여 호출하도록 한다. 

- 배송취소 서비스를 호출하기 위하여 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
# (order) CancellationService.java

package cafeteria.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="delivery", url="${feign.client.delivery.url}")
public interface CancellationService {

    @RequestMapping(method= RequestMethod.POST, path="/cancellations")
    public void save(@RequestBody Cancellation cancellation);

}
```

- 주문취소가 되어도 배송정보의 취소장부에 내역이 저장된다. (위에 REST API 테스트 내용 있음)


## 비동기식 호출 

배송 시스템은 음료시스템과 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 배송시스템이 유지보수로 인해 잠시 내려간 상태라도 음료를 제조하는데 문제가 없다:
```
#배송서비스(delivery)를 잠시 내려놓음
root@labs--619648044:/home/project/team/cafeTest/deliverycenter# kubectl delete deploy delivery
deployment.apps "delivery" deleted

#음료(drink)처리
root@seige-74d7df4cd9-7sckv:/# http PATCH http://drink:8080/drinks/2 status="Made"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Wed, 24 Feb 2021 13:00:33 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/2"
        },
        "self": {
            "href": "http://drink:8080/drinks/2"
        }
    },
    "createTime": "2021-02-24T10:27:20.846+0000",
    "orderId": 8,
    "phoneNumber": "01082947794",
    "productName": "coffee",
    "qty": 3,
    "status": "Made"
}

#배송 서비스 기동
root@labs--619648044:/home/project/team/cafeTest/delivery/kubernetes# kubectl apply -f deployment.yml
deployment.apps/delivery created

#배송정보 확인
root@seige-74d7df4cd9-7sckv:/# http http://delivery:8080/deliveries
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Wed, 24 Feb 2021 13:04:02 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "deliveries": [
            {
                "_links": {
                    "delivery": {
                        "href": "http://delivery:8080/deliveries/1"
                    },
                    "self": {
                        "href": "http://delivery:8080/deliveries/1"
                    }
                },
                "orderId": 8,
                "status": "Delivery Started"
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://delivery:8080/profile/deliveries"
        },
        "self": {
            "href": "http://delivery:8080/deliveries{?page,size,sort}",
            "templated": true
        }
    },
    "page": {
        "number": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    }
}
```

## CQRS / Meterialized View
배송센터는 배송페이지를 통해, 주문내역, 배송내역, 취소내역을 DB조인 없이 열람할 수 있다.
```
root@seige-74d7df4cd9-7sckv:/# http http://deliverycenter:8080/deliverypages
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Wed, 24 Feb 2021 13:08:58 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "deliverypages": [
            {
                "_links": {
                    "deliverypage": {
                        "href": "http://deliverycenter:8080/deliverypages/2"
                    },
                    "self": {
                        "href": "http://deliverycenter:8080/deliverypages/2"
                    }
                },
                "amt": 6000,
                "deliveryId": null,
                "orderId": 8,
                "status": "Delivery Cancelled"
            },
            {
                "_links": {
                    "deliverypage": {
                        "href": "http://deliverycenter:8080/deliverypages/3"
                    },
                    "self": {
                        "href": "http://deliverycenter:8080/deliverypages/3"
                    }
                },
                "amt": 6450,
                "deliveryId": null,
                "orderId": 9,
                "status": "Ordered"
            }
        ]
    },
    "_links": {
        "profile": {
            "href": "http://deliverycenter:8080/profile/deliverypages"
        },
        "search": {
            "href": "http://deliverycenter:8080/deliverypages/search"
        },
        "self": {
            "href": "http://deliverycenter:8080/deliverypages"
        }
    }
}

```

# 운영

## CI/CD 설정

각 구현체들은 각자의 source repository 에 구성되었고, 개인과제에서는 yml 파일을 사용해서 수동배포를 실행해 보았다.
```
1) 배포방식1
--delivery
mvn package
docker build -t 336676056763.dkr.ecr.ap-northeast-2.amazonaws.com/delivery:v1 .
docker login --username AWS -p $(aws ecr get-login-password --region ap-northeast-2) 336676056763.dkr.ecr.ap-northeast-2.amazonaws.com/
docker push 336676056763.dkr.ecr.ap-northeast-2.amazonaws.com/delivery:v1 
--ECR에서 푸시된 이미지확인
kubectl create deploy delivery --image=336676056763.dkr.ecr.ap-northeast-2.amazonaws.com/delivery:v1 
--배포된 이미지 Status 확인 (kubectl describe pod)
kubectl expose deploy delivery --type=ClusterIP --port=8080

2) 배포방식2
root@labs--619648044:/home/project/team/cafeteria-main/gateway/kubernetes# ls
deployment.yml  service.yaml
root@labs--619648044:/home/project/team/cafeteria-main/gateway# kubectl apply -f kubernetes/
deployment.apps/gateway created
service/gateway created
root@labs--619648044:/home/project/team/cafeteria-main/gateway# kubectl get pod -w
NAME                              READY   STATUS    RESTARTS   AGE
delivery-7f569d7d4f-68gwj         1/1     Running   0          25h
deliverycenter-6b985f5ff8-f8zpc   1/1     Running   0          25h
drink-74996f487f-bbm9x            1/1     Running   0          162m
gateway-56d9f65945-2rsrm          1/1     Running   0          7s
order-7ffd9b49b5-vfx7x            1/1     Running   0          21h
payment-84bbf45cd5-f7pd4          1/1     Running   0          22h
seige-74d7df4cd9-7sckv            1/1     Running   0          25h
```

## ConfigMap
Dockerfile의 spring.profiles.active=docker 부분을 환경변수를 지정해서 사용할 수 있도록 하였다.

```
# configmap.yaml

apiVersion: v1
kind: ConfigMap
metadata:
  name: spring
  namespace: default
data:
  profile: "docker"
  
# Dockerfile
FROM openjdk:8u212-jdk-alpine
COPY target/*SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx400M","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar","--spring.profiles.active=docker"]

root@labs--201874186:/home/project/cafe_delivery/delivery/kubernetes# kubectl logs -f delivery-6dfffc457d-c75lm | grep profile
2021-02-25 05:03:51.970  INFO 1 --- [           main] cafeteria.DeliveryApplication            : The following profiles are active: docker
```


## Liveness 설정
Pod 생성 시 준비되지 않은 상태에서 요청을 받아 오류가 발생하지 않도록 Liveness Probe를 설정했다.
```
# deployment.yml
 livenessProbe:
   httpGet:
   path: '/actuator/health'
   port: 8080
   initialDelaySeconds: 120
   timeoutSeconds: 2
   periodSeconds: 5
   failureThreshold: 5

>> 테스트 방법 : path 경로를 healthxxx 로 지정한 후 배포하여 정상적인 상태(health)를 방해하여 pod가 restart 되도록 유도함. 
root@labs--201874186:/home/project/cafe_delivery/delivery/kubernetes# kubectl get pods -w
NAME                              READY   STATUS    RESTARTS   AGE
delivery-8465d4494b-2j6t4         0/1     Running   0          4s
deliverycenter-6b985f5ff8-f8zpc   1/1     Running   0          42h
gateway-56d9f65945-2rsrm          1/1     Running   0          17h
order-7ffd9b49b5-vfx7x            1/1     Running   0          38h
payment-84bbf45cd5-f7pd4          1/1     Running   0          40h
seige-74d7df4cd9-7sckv            1/1     Running   0          42h
delivery-8465d4494b-2j6t4         1/1     Running   0          25s
delivery-8465d4494b-2j6t4         0/1     Running   1          2m22s
delivery-8465d4494b-2j6t4         1/1     Running   1          2m45s
delivery-8465d4494b-2j6t4         0/1     Running   2          4m48s
delivery-8465d4494b-2j6t4         1/1     Running   2          5m10s
delivery-8465d4494b-2j6t4         0/1     Running   3          7m12s
delivery-8465d4494b-2j6t4         1/1     Running   3          7m36s

```

## 셀프힐링(무정지배포:Readiness)
readinessProbe 를 설정하여 문제가 있을 경우 스스로 재기동 되도록 한다.
image 를 변경하면서 pod가 무정지 상태로 재기동 하는 것을 확인 할 수 있다. 
```
# deployment.yml
readinessProbe:
  httpGet:
    path: '/actuator/health'
    port: 8080
  initialDelaySeconds: 10
  timeoutSeconds: 2
  periodSeconds: 5
  failureThreshold: 10
	    
	    
root@labs--201874186:/home/project/cafe_delivery/delivery# kubectl set image deploy/delivery delivery=336676056763.dkr.ecr.ap-northeast-2.amazonaws.com/delivery:v2
deployment.apps/delivery image updated
root@labs--201874186:/home/project/cafe_delivery/delivery# kubectl get pods
NAME                              READY   STATUS    RESTARTS   AGE
delivery-6dfffc457d-csh4f         1/1     Running   0          95m
delivery-74d9dd445c-9c7wb         0/1     Running   0          21s
deliverycenter-6b985f5ff8-f8zpc   1/1     Running   0          44h
gateway-56d9f65945-2rsrm          1/1     Running   0          18h
order-7ffd9b49b5-vfx7x            1/1     Running   0          40h
payment-84bbf45cd5-f7pd4          1/1     Running   0          41h
seige-74d7df4cd9-7sckv            1/1     Running   0          44h
root@labs--201874186:/home/project/cafe_delivery/delivery# kubectl get pods -w
NAME                              READY   STATUS        RESTARTS   AGE
delivery-6dfffc457d-csh4f         0/1     Terminating   0          95m
delivery-74d9dd445c-9c7wb         1/1     Running       0          33s
deliverycenter-6b985f5ff8-f8zpc   1/1     Running       0          44h
gateway-56d9f65945-2rsrm          1/1     Running       0          18h
order-7ffd9b49b5-vfx7x            1/1     Running       0          40h
payment-84bbf45cd5-f7pd4          1/1     Running       0          41h
seige-74d7df4cd9-7sckv            1/1     Running       0          44h
delivery-6dfffc457d-csh4f         0/1     Terminating   0          95m
delivery-6dfffc457d-csh4f         0/1     Terminating   0          95m
.
.
NAME                              READY   STATUS    RESTARTS   AGE
delivery-74d9dd445c-9c7wb         1/1     Running   0          3m4s
deliverycenter-6b985f5ff8-f8zpc   1/1     Running   0          44h
gateway-56d9f65945-2rsrm          1/1     Running   0          18h
order-7ffd9b49b5-vfx7x            1/1     Running   0          40h
payment-84bbf45cd5-f7pd4          1/1     Running   0          41h
seige-74d7df4cd9-7sckv            1/1     Running   0          44h
```

