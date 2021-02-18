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

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReceipted_(@Payload Receipted receipted){

        if(receipted.isMe()){
            System.out.println("##### listener  : " + receipted.toJson());
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMade_(@Payload Made made){

        if(made.isMe()){
            System.out.println("##### listener  : " + made.toJson());
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrdered_(@Payload Ordered ordered){

        if(ordered.isMe()){
            System.out.println("##### listener  : " + ordered.toJson());
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCancelFailed_(@Payload CancelFailed cancelFailed){

        if(cancelFailed.isMe()){
            System.out.println("##### listener  : " + cancelFailed.toJson());
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentCanceled_(@Payload PaymentCanceled paymentCanceled){

        if(paymentCanceled.isMe()){
            System.out.println("##### listener  : " + paymentCanceled.toJson());
        }
    }

}
