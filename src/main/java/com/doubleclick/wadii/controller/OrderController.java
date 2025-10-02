package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.OrderDto;
import com.doubleclick.wadii.dto.SparePartsDto;
import com.doubleclick.wadii.entities.Order;
import com.doubleclick.wadii.entities.Service;
import com.doubleclick.wadii.entities.SpareParts;
import com.doubleclick.wadii.repository.OrderRepository;
import com.doubleclick.wadii.repository.ServiceRepository;
import com.doubleclick.wadii.repository.SparePartsRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/orders")
public class OrderController extends Controller<Order, OrderDto, Long> {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SparePartsRepository sparePartsRepository;
    private final ServiceRepository serviceRepository;

    @Override
    public ResponseEntity<Response<Order>> show(Long id) {
        return orderRepository.findById(id)
                .map(order -> Response.response(order, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no order with this id : " + id, ResponseType.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Response<Order>> insert(Authentication authentication, OrderDto orderDto) {
        List<SpareParts> sparePartsList = new ArrayList<>();

        Optional<User> userOptional = userRepository.findById(orderDto.getUserId());
        System.out.println(userOptional.get());
        List<Service> services = serviceRepository.findAllById(orderDto.getServicesIds());
        System.out.println(services);
        Order order = new Order();
        order.setDate(orderDto.getDate());
        order.setUser(userOptional.get());
        order.setComment(orderDto.getComment());
        order.setCarModelYear(orderDto.getCarModelYear());
        order.setServices(services);
        order = orderRepository.save(order);
        for (SparePartsDto sparePartsDto : orderDto.getSpareParts()) {
            SpareParts spareParts = new SpareParts();
            spareParts.setSparePartName(sparePartsDto.getSparePartName());
            spareParts.setOrder(order);
            sparePartsRepository.save(spareParts);
        }
        for (Service service : services) {
            if (service.getOrders() != null) {
                service.getOrders().add(order);
            } else {
                List<Order> orders = new ArrayList<>();
                orders.add(order);
                service.setOrders(orders);
            }
            serviceRepository.save(service);
        }
        return Response.response(order, "Order saved successfully", ResponseType.SUCCESS);
    }

    @Override
    public ResponseEntity<Response<Order>> update(OrderDto orderDto) {
        return null;
    }


    @Override
    public ResponseEntity<Response<Order>> delete(Long id) {
        Optional<Order> orderOptional = orderRepository.findById(id);
        if (orderOptional.isPresent()) {
            orderRepository.deleteById(id);
            return Response.response(null, "order deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no order with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Order>>> readAll() {
        return Response.response(orderRepository.findAll(), "All orders", ResponseType.SUCCESS);
    }
}
