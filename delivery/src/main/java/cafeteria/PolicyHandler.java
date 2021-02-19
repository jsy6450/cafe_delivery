package cafeteria;

import cafeteria.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @Autowired
    DeliveryRepository DeliveryRepository;
    
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMade_(@Payload Made made){

        if(made.isMe()){
            Delivery delivery = new Delivery();
            delivery.setOrderId(made.getId());
            delivery.setStatus("Delivery Started");
                         
            DeliveryRepository.save(delivery);
            System.out.println("##### listener  : " + made.toJson());
        }
    }

}
