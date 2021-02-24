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
![hexa](https://user-images.githubusercontent.com/31643538/108976354-c4abd900-76ca-11eb-9864-1523cdbb5b84.png)


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

- 배송서비스를 호출하기 위하여 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

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


## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 상점시스템으로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 상점 시스템의 처리를 위하여 결제주문이 블로킹 되지 않아도록 처리한다.
 
- 이를 위하여 결제이력에 기록을 남긴 후에 곧바로 결제승인이 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```
package cafeteria;

@Entity
@Table(name="Payment")
public class Payment {

 :
    @PostPersist
    public void onPostPersist(){
        PaymentApproved paymentApproved = new PaymentApproved();
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();

    }

}
```
- 음료 서비스에서는 결제승인 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
package cafeteria;

:

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_(@Payload PaymentApproved paymentApproved){

        if(paymentApproved.isMe()){
            System.out.println("##### listener  : " + paymentApproved.toJson());
            
            Drink drink = new Drink();
            drink.setOrderId(paymentApproved.getOrderId());
            drink.setStatus(paymentApproved.getStatus());
            drinkRepository.save(drink);
            
        }
    }

```
Replica를 추가했을 때 중복없이 수신할 수 있도록 서비스별 Kafka Group을 동일하게 지정했다.
```
spring:
  cloud:
    stream:
      bindings:
        event-in:
          group: drink
          destination: cafeteria
          contentType: application/json
        :
```
실제 구현에서 카톡은 화면에 출력으로 대체하였다.
  
```    
  @StreamListener(KafkaProcessor.INPUT)
  def whenReceipted_then_UPDATE_3(@Payload made :Made) {
    try {
      if (made.isMe()) {
        
        val message :KakaoMessage = new KakaoMessage()
        message.phoneNumber = made.phoneNumber
        message.message = s"""Your Order is ${made.status}\nCome and Take it, Please!"""
        kakaoService.sendKakao(message)
      }
    } catch {
      case e :Exception => e.printStackTrace()
    }
  }

@Component
class KakaoServiceImpl extends KakaoService {
  
	override def sendKakao(message :KakaoMessage) {
		logger.info(s"\nTo. ${message.phoneNumber}\n${message.message}\n")
	}
}

```

음료 시스템은 주문/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 음료시스템이 유지보수로 인해 잠시 내려간 상태라도 주문을 받는데 문제가 없다:
```
# 음료 서비스 (drink) 를 잠시 내려놓음
$ kubectl delete deploy drink
deployment.apps "drink" deleted

#주문처리
root@siege-5b99b44c9c-8qtpd:/# http http://order:8080/orders phoneNumber="01012345679" productName="coffee" qty=3 amt=5000
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Sat, 20 Feb 2021 14:53:25 GMT
Location: http://order:8080/orders/7
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/7"
        },
        "self": {
            "href": "http://order:8080/orders/7"
        }
    },
    "amt": 5000,
    "createTime": "2021-02-20T14:53:25.115+0000",
    "phoneNumber": "01012345679",
    "productName": "coffee",
    "qty": 3,
    "status": "Ordered"
}
#음료 서비스 기동
kubectl apply -f deployment.yml
deployment.apps/drink created

#음료등록 확인

root@siege-5b99b44c9c-8qtpd:/# http http://drink:8080/drinks/search/findByOrderId?orderId=7
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 14:54:14 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "drinks": [
            {
                "_links": {
                    "drink": {
                        "href": "http://drink:8080/drinks/4"
                    },
                    "self": {
                        "href": "http://drink:8080/drinks/4"
                    }
                },
                "createTime": "2021-02-20T14:53:25.194+0000",
                "orderId": 7,
                "phoneNumber": "01012345679",
                "productName": "coffee",
                "qty": 3,
                "status": "PaymentApproved"
            }
        ]
    },
    "_links": {
        "self": {
            "href": "http://drink:8080/drinks/search/findByOrderId?orderId=7"
        }
    }
}

```


## Saga Pattern / 보상 트랜잭션

음료 주문 취소는 바리스타가 음료 접수하기 전에만 취소가 가능하다.
음료 접수 후에 취소할 경우 보상트랜재션을 통하여 취소를 원복한다.
음료 주문 취소는 Saga Pattern으로 만들어져 있어 바리스타가 음료를 이미 접수하였을 경우 취소실패를 Event로 publish하고
Order 서비스에서 취소실패 Event를 Subscribe하여 주문취소를 원복한다.
```
# 주문
root@siege-5b99b44c9c-8qtpd:/# http http://order:8080/orders/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:58:19 GMT
Transfer-Encoding: chunked
{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/5"
        },
        "self": {
            "href": "http://order:8080/orders/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T08:51:17.441+0000",
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Ordered"
}

# 결제 상태 확인 
root@siege-5b99b44c9c-8qtpd:/# http http://payment:8080/payments/search/findByOrderId?orderId=5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:58:54 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "payments": [
            {
                "_links": {
                    "payment": {
                        "href": "http://payment:8080/payments/5"
                    },
                    "self": {
                        "href": "http://payment:8080/payments/5"
                    }
                },
                "amt": 100,
                "createTime": "2021-02-20T08:51:17.452+0000",
                "orderId": 5,
                "phoneNumber": "01033132570",
                "status": "PaymentApproved"
            }
        ]
    },
    "_links": {
        "self": {
            "href": "http://payment:8080/payments/search/findByOrderId?orderId=5"
        }
    }
}

# 음료 상태 확인
root@siege-5b99b44c9c-8qtpd:/# http http://drink:8080/drinks/search/findByOrderId?orderId=5                              
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:52:14 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "drinks": [
            {
                "_links": {
                    "drink": {
                        "href": "http://drink:8080/drinks/5"
                    },
                    "self": {
                        "href": "http://drink:8080/drinks/5"
                    }
                },
                "createTime": "2021-02-20T08:51:17.515+0000",
                "orderId": 5,
                "phoneNumber": "01033132570",
                "productName": "coffee",
                "qty": 2,
                "status": "PaymentApproved"
            }
        ]
    },
    "_links": {
        "self": {
            "href": "http://drink:8080/drinks/search/findByOrderId?orderId=5"
        }
    }
}

# 음료 접수
root@siege-5b99b44c9c-8qtpd:/# http patch http://drink:8080/drinks/5 status="Receipted"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:53:29 GMT
Transfer-Encoding: chunked
{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/5"
        },
        "self": {
            "href": "http://drink:8080/drinks/5"
        }
    },
    "createTime": "2021-02-20T08:51:17.515+0000",
    "orderId": 5,
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Receipted"
}

# 주문 취소
root@siege-5b99b44c9c-8qtpd:/# http patch http://order:8080/orders/5 status="OrderCanceled"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:54:29 GMT
Transfer-Encoding: chunked
{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/5"
        },
        "self": {
            "href": "http://order:8080/orders/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T08:51:17.441+0000",
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "OrderCanceled"
}

# 주문 조회
root@siege-5b99b44c9c-8qtpd:/# http http://order:8080/orders/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 09:07:49 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/5"
        },
        "self": {
            "href": "http://order:8080/orders/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T09:07:24.114+0000",
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Ordered"
}

# 결제 상태 확인
root@siege-5b99b44c9c-8qtpd:/# http http://payment:8080/payments/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 09:21:59 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "payment": {
            "href": "http://payment:8080/payments/5"
        },
        "self": {
            "href": "http://payment:8080/payments/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T08:51:17.452+0000",
    "orderId": 5,
    "phoneNumber": "01033132570",
    "status": "PaymentApproved"
}

# 음료 상태 확인
root@siege-5b99b44c9c-8qtpd:/# http http://drink:8080/drinks/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 09:22:47 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/5"
        },
        "self": {
            "href": "http://drink:8080/drinks/5"
        }
    },
    "createTime": "2021-02-20T08:51:17.515+0000",
    "orderId": 5,
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Receipted"
}

```

CancelFailed Event는 Customercenter 서비스에서도 subscribe하여 카카오톡으로 취소 실패된 내용을 전달한다.
```
2021-02-20 09:08:42.668  INFO 1 --- [container-0-C-1] cafeteria.external.KakaoServiceImpl      :
To. 01033132570
Your Order is already started. You cannot cancel!!
```

## CQRS / Meterialized View
CustomerCenter의 Mypage를 구현하여 Order 서비스, Payment 서비스, Drink 서비스의 데이터를 Composite서비스나 DB Join없이 조회할 수 있다.
```
root@siege-5b99b44c9c-8qtpd:/# http http://customercenter:8080/mypages/search/findByPhoneNumber?phoneNumber="01012345679"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Sat, 20 Feb 2021 14:57:45 GMT
Transfer-Encoding: chunked

[
    {
        "amt": 5000,
        "id": 4544,
        "orderId": 4,
        "phoneNumber": "01012345679",
        "productName": "coffee",
        "qty": 3,
        "status": "Made"
    },
    {
        "amt": 5000,
        "id": 4545,
        "orderId": 5,
        "phoneNumber": "01012345679",
        "productName": "coffee",
        "qty": 3,
        "status": "Ordered"
    },
    {
        "amt": 5000,
        "id": 4546,
        "orderId": 6,
        "phoneNumber": "01012345679",
        "productName": "coffee",
        "qty": 3,
        "status": "Receipted"
    },
    {
        "amt": 5000,
        "id": 4547,
        "orderId": 7,
        "phoneNumber": "01012345679",
        "productName": "coffee",
        "qty": 3,
        "status": "Ordered"
    }
]

```

# 운영

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

## CI/CD 설정


각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS를 사용하였으며, pipeline build script 는 각 프로젝트 폴더 아래에 buildspec.yml 에 포함되었다.


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

## 모니터링
모니터링을 위하여 monitor namespace에 Prometheus와 Grafana를 설치하였다.

```
$ kubectl get deploy -n monitor
NAME                                  READY   UP-TO-DATE   AVAILABLE   AGE
cafe-grafana                          1/1     1            1           2d
cafe-kube-prometheus-stack-operator   1/1     1            1           2d
cafe-kube-state-metrics               1/1     1            1           2d
```
grafana 접근을 위해서 grafana의 Service는 LoadBalancer로 생성하였다.
```
$ kubectl get svc -n monitor
NAME                                      TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)                      AGE
alertmanager-operated                     ClusterIP      None           <none>         9093/TCP,9094/TCP,9094/UDP   2d
cafe-grafana                              LoadBalancer   10.68.15.180   34.84.30.157   80:32120/TCP                 2d
cafe-kube-prometheus-stack-alertmanager   ClusterIP      10.68.14.210   <none>         9093/TCP                     2d
cafe-kube-prometheus-stack-operator       ClusterIP      10.68.3.201    <none>         443/TCP                      2d
cafe-kube-prometheus-stack-prometheus     ClusterIP      10.68.6.110    <none>         9090/TCP                     2d
cafe-kube-state-metrics                   ClusterIP      10.68.9.55     <none>         8080/TCP                     2d
cafe-prometheus-node-exporter             ClusterIP      10.68.9.213    <none>         9100/TCP                     2d
prometheus-operated                       ClusterIP      None           <none>         9090/TCP                     2d
```
![image](https://user-images.githubusercontent.com/75828964/108602078-625d8a80-73e3-11eb-9517-486c2b5bd584.png)
![image](https://user-images.githubusercontent.com/75828964/108602105-89b45780-73e3-11eb-9bdc-268c1f929511.png)

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

## Persistence Volum Claim
서비스의 log를 persistence volum을 사용하여 재기동후에도 남아 있을 수 있도록 하였다.
```

# application.yml

:
server:
  tomcat:
    accesslog:
      enabled: true
      pattern:  '%h %l %u %t "%r" %s %bbyte %Dms'
    basedir: /logs/drink

logging:
  path: /logs/drink
  file:
    max-history: 30
  level:
    org.springframework.cloud: debug

# deployment.yaml

apiVersion: apps/v1
kind: Deployment
metadata:
  name: drink
  labels:
    app: drink
spec:
  replicas: 1
  selector:
    matchLabels:
      app: drink
  template:
    metadata:
      labels:
        app: drink
    spec:
      containers:
      - name: drink
        image: beatific/drink:v1
        :
        volumeMounts:
        - name: logs
          mountPath: /logs
      volumes:
      - name: logs
        persistentVolumeClaim:
          claimName: logs

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
drink deployment를 삭제하고 재기동해도 log는 삭제되지 않는다.

```
$ kubectl delete -f drink/kubernetes/deployment.yml
deployment.apps "drink" deleted

$ kubectl apply -f drink/kubernetes/deployment.yml
deployment.apps/drink created

$ kubectl exec -it drink-7cb565cb4-8c7pq -- /bin/sh
/ # ls -l /logs/drink/
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
