package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Order;
import com.doubleclick.wadii.entities.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<List<Order>> findOrderByUserId(Long userId);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.services s JOIN o.user u WHERE u.id = :userId")
    List<Order> findOrdersByUserId(@Param("userId") Long userId);

    /** Row lock used while a response is accepted so two requests cannot both win. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.status IN :statuses AND o.acceptedProvider IS NOT NULL AND "
            + "((o.user.id = :a AND o.acceptedProvider.user.id = :b) OR (o.user.id = :b AND o.acceptedProvider.user.id = :a))")
    boolean existsConnection(@Param("a") Long a, @Param("b") Long b, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT DISTINCT o.acceptedProvider.user.id FROM Order o WHERE o.user.id = :userId "
            + "AND o.acceptedProvider IS NOT NULL AND o.status IN :statuses")
    List<Long> findAcceptedProviderUserIdsOfCustomer(@Param("userId") Long userId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT DISTINCT o.user.id FROM Order o WHERE o.acceptedProvider.user.id = :userId AND o.status IN :statuses")
    List<Long> findCustomerIdsOfAcceptedProviderUser(@Param("userId") Long userId, @Param("statuses") Collection<OrderStatus> statuses);

    /** True when the customer has a completed order with this provider as the winner. */
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.user.id = :userId AND o.acceptedProvider.id = :providerId "
            + "AND o.status = com.doubleclick.wadii.entities.OrderStatus.COMPLETED")
    boolean existsCompletedWithProvider(@Param("userId") Long userId, @Param("providerId") Long providerId);
}
