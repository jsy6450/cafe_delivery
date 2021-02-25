# cafe_delivery
# Table of contents

- [음료배달](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [DDD 의 적용](#ddd-의-적용)
    - [API Gateway](#API-GATEWAY)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
    - [Saga Pattern / 보상 트랜잭션](#Saga-Pattern--보상-트랜잭션)
    - [CQRS / Meterialized View](#CQRS--Meterialized-View)
  - [운영](#운영)
    - [Liveness / Readiness 설정](#Liveness--Readiness-설정)
    - [CI/CD 설정](#cicd-설정)
    - [셀프힐링](#셀프힐링)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)
    - [모니터링](#모니터링)
    - [Persistence Volum Claim](#Persistence-Volum-Claim)
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


## Persistence Volum Claim
서비스의 log를 persistence volum을 사용하여 재기동후에도 남아 있을 수 있도록 하였다.

```
# application.yml

server:
  tomcat:
    accesslog:
      enabled: true
      pattern:  '%h %l %u %t "%r" %s %bbyte %Dms'
    basedir: /logs/delivery

logging:
  path: /logs/delivery
  file:
    max-history: 30
  level:
    org.springframework.cloud: debug

# deployment.yaml

volumeMounts:
  - name: logs
    mountPath: /logs
volumes:
  - name: logs
    persistentVolumeClaim:
    claimName: delivery-logs

# pvc.yaml

apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: logs
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```
delivery deployment를 삭제하고 재기동해도 log는 삭제되지 않는다.

```
root@labs--619648044:/home/project/team/cafeTest/delivery# kubectl delete deploy delivery
deployment.apps "delivery" deleted

root@labs--619648044:/home/project/team/cafeTest/delivery# kubectl apply -f kubernetes/deployment.yml
deployment.apps/delivery created

$ kubectl exec -it drink-7cb565cb4-8c7pq -- /bin/sh
/ # ls -l /logs/delivery/

다시 시작!!!!!

total 5568
drwxr-xr-x    2 root     root          4096 Feb 20 00:00 logs
-rw-r--r--    1 root     root       4626352 Feb 20 16:34 spring.log
-rw-r--r--    1 root     root        177941 Feb 20 08:17 spring.log.2021-02-19.0.gz
-rw-r--r--    1 root     root        235383 Feb 20 15:48 spring.log.2021-02-20.0.gz
-rw-r--r--    1 root     root        210417 Feb 20 15:55 spring.log.2021-02-20.1.gz
-rw-r--r--    1 root     root        214386 Feb 20 15:55 spring.log.2021-02-20.2.gz
-rw-r--r--    1 root     root        214686 Feb 20 16:01 spring.log.2021-02-20.3.gz
drwxr-xr-x    3 root     root          4096 Feb 19 17:34 work

```

## ConfigMap / Secret
mongo db의 database이름과 username, password는 환경변수를 지정해서 사용핳 수 있도록 하였다.
database 이름은 kubernetes의 configmap을 사용하였고 username, password는 secret을 사용하여 지정하였다.

```
# secret 생성
kubectl create secret generic mongodb --from-literal=username=mongodb --from-literal=password=mongodb --namespace cafeteria

# configmap.yaml

apiVersion: v1
kind: ConfigMap
metadata:
  name: mongodb
  namespace: cafeteria
data:
  database: "cafeteria"
  

# application.yml

spring:
  data:
    mongodb:
      uri: mongodb://my-mongodb-0.my-mongodb-headless.mongodb.svc.cluster.local:27017,my-mongodb-1.my-mongodb-headless.mongodb.svc.cluster.local:27017
      database: ${MONGODB_DATABASE}
      username: ${MONGODB_USERNAME}
      password: ${MONGODB_PASSWORD}

#buildspec.yaml
spec:
containers:
  - name: $_PROJECT_NAME
    image: $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$_PROJECT_NAME:$CODEBUILD_RESOLVED_SOURCE_VERSION
    ports:
    - containerPort: 8080
    env:
    - name: SPRING_PROFILES_ACTIVE
      value: "docker"
    - name: MONGODB_DATABASE
      valueFrom:
	configMapKeyRef:
	  name: mongodb
	  key: database
    - name: MONGODB_USERNAME
      valueFrom:
	secretKeyRef:
	  name: mongodb
	  key: username
    - name: MONGODB_PASSWORD
      valueFrom:
	secretKeyRef:
	  name: mongodb
	  key: password
```

### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 


- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다:
```
$ kubectl get pods
NAME                              READY   STATUS    RESTARTS   AGE
customercenter-7f57cf5f9f-csp2b   1/1     Running   1          20h
drink-7cb565cb4-d2vwb             1/1     Running   0          37m
gateway-5dd866cbb6-czww9          1/1     Running   0          3d1h
order-595c9b45b9-xppbf            1/1     Running   0          36m
payment-698bfbdf7f-vp5ft          1/1     Running   0          2m32s
siege-5b99b44c9c-8qtpd            1/1     Running   0          3d1h


$ kubectl autoscale deploy payment --min=1 --max=10 --cpu-percent=15
horizontalpodautoscaler.autoscaling/payment autoscaled

$ kubectl get hpa
NAME      REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
payment   Deployment/payment   2%/15%    1         10        1          2m35s

# CB 에서 했던 방식대로 워크로드를 2분 동안 걸어준다.

# siege -c100 -t60s --content-type "application/json" 'http://order:8080/orders POST {"phoneNumber":"01087654321", "productName":"coffee", "qty":2, "amt":1000}'
** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...siege aborted due to excessive socket failure; you
can change the failure threshold in $HOME/.siegerc

Transactions:                    626 hits
Availability:                  35.79 %
Elapsed time:                  52.29 secs
Data transferred:               1.06 MB
Response time:                  6.95 secs
Transaction rate:              11.97 trans/sec
Throughput:                     0.02 MB/sec
Concurrency:                   83.23
Successful transactions:         626
Failed transactions:            1123
Longest transaction:           30.08
Shortest transaction:           0.00

$ kubectl get pods
NAME                              READY   STATUS    RESTARTS   AGE
customercenter-7f57cf5f9f-csp2b   1/1     Running   3          21h
drink-7cb565cb4-d2vwb             1/1     Running   0          97m
gateway-5dd866cbb6-czww9          1/1     Running   0          3d2h
order-595c9b45b9-xppbf            1/1     Running   1          96m
payment-698bfbdf7f-2bc56          1/1     Running   0          2m55s
payment-698bfbdf7f-bcmb9          1/1     Running   0          3m42s
payment-698bfbdf7f-f5kf2          1/1     Running   0          3m42s
payment-698bfbdf7f-kclfb          1/1     Running   0          2m55s
payment-698bfbdf7f-vmcd4          1/1     Running   0          2m40s
payment-698bfbdf7f-vp5ft          1/1     Running   0          62m
payment-698bfbdf7f-wg769          1/1     Running   0          2m40s
payment-698bfbdf7f-xbdqp          1/1     Running   0          2m40s
payment-698bfbdf7f-z8trs          1/1     Running   0          2m55s
payment-698bfbdf7f-z9hk7          1/1     Running   0          2m40s
siege-5b99b44c9c-8qtpd            1/1     Running   0          3d2h
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy payment -w
```
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다:
```
NAME      READY   UP-TO-DATE   AVAILABLE   AGE
payment   1/1     1            1           2m24s
payment   1/4     1            1           3m12s
payment   1/4     1            1           3m12s
payment   1/4     1            1           3m12s
payment   1/4     4            1           3m12s
payment   1/8     4            1           3m12s
payment   1/8     4            1           3m12s
payment   1/8     4            1           3m12s
payment   1/8     8            1           3m12s
payment   1/10    8            1           3m28s
payment   1/10    8            1           3m28s
payment   1/10    8            1           3m28s
payment   1/10    10           1           3m28s
payment   2/10    10           2           5m17s
payment   3/10    10           3           5m21s
payment   4/10    10           4           5m23s
:

# siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 

Transactions:		        5078 hits
Availability:		       92.45 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```

## Liveness / Readiness 설정
Pod 생성 시 준비되지 않은 상태에서 요청을 받아 오류가 발생하지 않도록 Readiness Probe와 Liveness Probe를 설정했다.
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order
  labels:
    app: order
spec:
  :
        readinessProbe:
          httpGet:
            path: '/actuator/health'
            port: 8080
          initialDelaySeconds: 10 
          timeoutSeconds: 2 
          periodSeconds: 5 
          failureThreshold: 10
        livenessProbe:
          httpGet:
            path: '/actuator/health'
            port: 8080
          initialDelaySeconds: 120
          timeoutSeconds: 2
          periodSeconds: 5
          failureThreshold: 5

```

## 셀프힐링
livenessProbe를 설정하여 문제가 있을 경우 스스로 재기동 되도록 한다.
```	  
# mongodb down
$ helm delete my-mongodb --namespace mongodb
release "my-mongodb" uninstalled

# mongodb start
$ helm install my-mongodb bitnami/mongodb --namespace mongodb -f values.yaml

# mongodb를 사용하는 customercenter 서비스가 liveness에 실패하여 재기동하고 새롭게 시작한 mongo db에 접속한다. 

$ kubectl describe pods customercenter-7f57cf5f9f-csp2b
:
Events:
  Type     Reason     Age                   From     Message
  ----     ------     ----                  ----     -------
  Normal   Killing    12m (x2 over 6h21m)   kubelet  Container customercenter failed liveness probe, will be restarted
  Normal   Pulling    12m (x3 over 20h)     kubelet  Pulling image "beatific/customercenter:v6"
  Normal   Created    12m (x3 over 20h)     kubelet  Created container customercenter
  Normal   Started    12m (x3 over 20h)     kubelet  Started container customercenter
  Normal   Pulled     12m (x3 over 20h)     kubelet  Successfully pulled image "beatific/customercenter:v6"
  Warning  Unhealthy  11m (x30 over 20h)    kubelet  Readiness probe failed: Get http://10.64.1.29:8080/actuator/health: dial tcp 10.64.1.29:8080: connect: connection refused
  Warning  Unhealthy  11m (x17 over 6h21m)  kubelet  Readiness probe failed: Get http://10.64.1.29:8080/actuator/health: net/http: request canceled (Client.Timeout exceeded while awaiting headers)
  Warning  Unhealthy  14s                   kubelet  Readiness probe failed: HTTP probe failed with statuscode: 503
  Warning  Unhealthy  11s (x13 over 6h21m)  kubelet  Liveness probe failed: Get http://10.64.1.29:8080/actuator/health: net/http: request canceled (Client.Timeout exceeded while awaiting headers)
  
$ kubectl get pods -w
NAME                              READY   STATUS    RESTARTS   AGE
customercenter-7f57cf5f9f-csp2b   1/1     Running   0          20h
drink-7cb565cb4-d2vwb             1/1     Running   0          59m
gateway-5dd866cbb6-czww9          1/1     Running   0          3d1h
order-595c9b45b9-xppbf            1/1     Running   0          58m
payment-698bfbdf7f-vp5ft          1/1     Running   0          24m
siege-5b99b44c9c-8qtpd            1/1     Running   0          3d1h
customercenter-7f57cf5f9f-csp2b   0/1     Running   1          20h
customercenter-7f57cf5f9f-csp2b   1/1     Running   1          20h

```
## 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함

시나리오는 단말앱(order)-->결제(payment) 호출 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 결제 요청이 과도할 경우 CB 를 통하여 장애격리.

- Hystrix 를 설정:  요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정
```
# application.yml

feign:
  hystrix:
    enabled: false 

hystrix:
  command:
    default:
      execution:
        isolation:
          strategy: THREAD
          thread:
            timeoutInMilliseconds: 610         #설정 시간동안 처리 지연발생시 timeout and 설정한 fallback 로직 수행     
      circuitBreaker:
        requestVolumeThreshold: 20           # 설정수 값만큼 요청이 들어온 경우만 circut open 여부 결정 함
        errorThresholdPercentage: 10        # requestVolumn값을 넘는 요청 중 설정 값이상 비율이 에러인 경우 circuit open
        sleepWindowInMilliseconds: 5000    # 한번 오픈되면 얼마나 오픈할 것인지 
      metrics:
        rollingStats:
          timeInMilliseconds: 10000   

```

- 피호출 서비스(결제:payment) 의 임의 부하 처리 - 400 밀리에서 증감 220 밀리 정도 왔다갔다 하게
```
# (payment) Payment.java (Entity)

    @PrePersist
    public void onPrePersist(){  //결제이력을 저장한 후 적당한 시간 끌기

        :
        
        try {
            Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시
```
# siege -c100 -t60s --content-type "application/json" 'http://order:8080/orders POST {"phoneNumber":"01087654321", "productName":"coffee", "qty":2, "amt":1000}'
```
![image](https://user-images.githubusercontent.com/75828964/106759329-f9051a00-6675-11eb-93fa-daf7924d5718.png)
![image](https://user-images.githubusercontent.com/75828964/106759337-fd313780-6675-11eb-90ac-e62f5fbc6648.png)

- 운영시스템은 죽지 않고 지속적으로 CB 에 의하여 적절히 회로가 열림과 닫힘이 벌어지면서 자원을 보호하고 있음을 보여줌. 하지만, 63.55% 가 성공하였고, 46%가 실패했다는 것은 고객 사용성에 있어 좋지 않기 때문에 Retry 설정과 동적 Scale out (replica의 자동적 추가,HPA) 을 통하여 시스템을 확장 해주는 후속처리가 필요.

- Retry 의 설정 (istio)
- Availability 가 높아진 것을 확인 (siege)



## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
siege -c100 -t120S -r10 --content-type "application/json" 'localhost:8081/orders POST {"phoneNumber": "01012345678","productName": "coffee","qty": 2,"amt": 1000}'

** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
:

```

- 새버전으로의 배포 시작
```
kubectl set image deployment/drink drink=beatific/order:v2
```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
```
Transactions:		        3078 hits
Availability:		       70.45 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```
배포기간중 Availability 가 평소 100%에서 70% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:

```
# deployment.yaml 의 readiness probe 의 설정:


kubectl apply -f kubernetes/deployment.yaml
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
Transactions:		        3078 hits
Availability:		       100 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.

