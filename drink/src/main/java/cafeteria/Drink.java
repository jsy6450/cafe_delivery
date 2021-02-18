package cafeteria;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Drink_table")
public class Drink {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private String status;
    private String phoneNumber;
    private Date createTime;

    @PostPersist
    public void onPostPersist(){
        OrderInfoReceived orderInfoReceived = new OrderInfoReceived();
        BeanUtils.copyProperties(this, orderInfoReceived);
        orderInfoReceived.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        Receipted receipted = new Receipted();
        BeanUtils.copyProperties(this, receipted);
        receipted.publishAfterCommit();


        Made made = new Made();
        BeanUtils.copyProperties(this, made);
        made.publishAfterCommit();


        DrinkCanceled drinkCanceled = new DrinkCanceled();
        BeanUtils.copyProperties(this, drinkCanceled);
        drinkCanceled.publishAfterCommit();


    }

    @PrePersist
    public void onPrePersist(){
        CancelFailed cancelFailed = new CancelFailed();
        BeanUtils.copyProperties(this, cancelFailed);
        cancelFailed.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }




}
