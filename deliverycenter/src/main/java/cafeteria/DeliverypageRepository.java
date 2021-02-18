package cafeteria;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeliverypageRepository extends CrudRepository<Deliverypage, Long> {

    List<> findByOrderId(Long orderId);
    List<> findByOrderId(Long orderId);

}