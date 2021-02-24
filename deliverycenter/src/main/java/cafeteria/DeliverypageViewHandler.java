package cafeteria;

import cafeteria.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class DeliverypageViewHandler {


    @Autowired
    private DeliverypageRepository deliverypageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrdered_then_CREATE_1 (@Payload Ordered ordered) {
        try {
            if (ordered.isMe()) {
                // view 객체 생성
                Deliverypage deliverypage = new Deliverypage();
                // view 객체에 이벤트의 Value 를 set 함
                deliverypage.setOrderId(ordered.getId());
                deliverypage.setAmt(ordered.getAmt());
                deliverypage.setStatus(ordered.getStatus());
                // view 레파지 토리에 save
                deliverypageRepository.save(deliverypage);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenShipped_then_UPDATE_1(@Payload Shipped shipped) {
        try {
            if (shipped.isMe()) {
                // view 객체 조회
                List<Deliverypage> deliverypageList = deliverypageRepository.findByOrderId(shipped.getOrderId());
                for(Deliverypage deliverypage  : deliverypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                     deliverypage.setDeliveryId(shipped.getId());
                     deliverypage.setStatus(shipped.getStatus());
                    // view 레파지 토리에 save
                    deliverypageRepository.save(deliverypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
     public void whenDeliveryCanceled_then_UPDATE_2(@Payload DeliveryCanceled deliveryCanceled) {
        try {
            if (deliveryCanceled.isMe()) {
                // view 객체 조회
                List<Deliverypage> deliverypageList = deliverypageRepository.findByOrderId(deliveryCanceled.getOrderId());
                for(Deliverypage deliverypage  : deliverypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    deliverypage.setStatus(deliveryCanceled.getStatus());
                    // view 레파지 토리에 save
                    deliverypageRepository.save(deliverypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
