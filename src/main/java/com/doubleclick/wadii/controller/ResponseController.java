package com.doubleclick.wadii.controller;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ResponseDto;
import com.doubleclick.wadii.dto.SparePartsPriceDto;
import com.doubleclick.wadii.entities.*;
import com.doubleclick.wadii.repository.OrderRepository;
import com.doubleclick.wadii.repository.ProviderRepository;
import com.doubleclick.wadii.repository.ResponseRepository;
import com.doubleclick.wadii.repository.SparePartsPriceRepository;
import com.doubleclick.wadii.ts.Controller;
import com.doubleclick.wadii.utils.Response;
import com.doubleclick.wadii.utils.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/responses")

public class ResponseController extends Controller<Responses, ResponseDto, Long> {

    private final UserRepository userRepository;
    private final ResponseRepository responseRepository;
    private final ProviderRepository providerRepository;
    private final SparePartsPriceRepository sparePartsPriceRepository;
    private final OrderRepository orderRepository;

    @Override
    public ResponseEntity<Response<Responses>> show(Long id) {
        return responseRepository.findById(id)
                .map(responses -> Response.response(responses, "Done", ResponseType.SUCCESS))
                .orElseGet(() -> Response.response(null, "there is no response with this id : " + id, ResponseType.NOT_FOUND));
    }

    //    @Override
//    public ResponseEntity<Response<Responses>> insert(Authentication authentication, ResponseDto responseDto) {
//        if (responseDto.isNotEmpty()) {
//            Optional<Provider> providerOptional = providerRepository.findById(responseDto.getProviderId());
//            if (providerOptional.isPresent()) {
//                Responses responses = new Responses();
//                responses.setId(responseDto.getId());
//                responses.setComment(responseDto.getComment());
//                responses.setProvider(providerOptional.get());
//                responses.setOrder(Objects.requireNonNull(orderController.show(responseDto.getOrderId()).getBody()).getData());
//                responses = responseRepository.save(responses);
//                if (responseDto.getSparePartsPrice() != null && !responseDto.getSparePartsPrice().isEmpty()) {
//                    for (SparePartsPriceDto sparePartsPriceDto : responseDto.getSparePartsPrice()) {
//                        SparePartsPrice sparePartsPrice = new SparePartsPrice();
//                        sparePartsPrice.setResponse(responses);
//                        sparePartsPrice.setPrice(sparePartsPriceDto.getPrice());
//                        sparePartsPrice.setSparePart(new SpareParts(sparePartsPriceDto.getId()));
//                        sparePartsPrice = sparePartsPriceRepository.save(sparePartsPrice);
//                    }
//                }
//                return Response.response(responses, "Response saved successfully", ResponseType.SUCCESS);
//            } else {
//                return Response.response(null, "there is no provider with this id : " + responseDto.getProviderId(), ResponseType.NOT_FOUND);
//            }
//        } else {
//            return Response.response(null, "name is empty", ResponseType.ERROR);
//        }
//    }
    @Override
    public ResponseEntity<Response<Responses>> insert(Authentication authentication, @RequestBody ResponseDto responseDto) {
        if (responseDto.isNotEmpty()) {
            Provider provider = providerRepository.findById(responseDto.getProviderId())
                    .orElseThrow(() -> new RuntimeException("No provider with id: " + responseDto.getProviderId()));

            Order order = orderRepository.findById(responseDto.getOrderId())
                    .orElseThrow(() -> new RuntimeException("No order with id: " + responseDto.getOrderId()));

            Responses responses = new Responses();
            responses.setComment(responseDto.getComment());
            responses.setLatitude(responseDto.getLatitude());
            responses.setLongitude(responseDto.getLongitude());
            responses.setProvider(provider);
            responses.setOrder(order);
            responses.setResponsesState(ResponsesState.PENDING);
            responses = responseRepository.save(responses);

            List<SparePartsPriceDto> priceDtos = responseDto.getSparePartsPrice();
            if (priceDtos != null && !priceDtos.isEmpty()) {
                List<SparePartsPrice> sparePartsPriceList = new ArrayList<>();
                for (SparePartsPriceDto sparePartsPriceDto : priceDtos) {
                    SparePartsPrice sparePartsPrice = new SparePartsPrice();
                    sparePartsPrice.setResponse(responses);
                    sparePartsPrice.setPrice(sparePartsPriceDto.getPrice());
                    sparePartsPrice.setSparePart(new SpareParts(sparePartsPriceDto.getId()));
                    sparePartsPriceList.add(sparePartsPrice);
                }
                sparePartsPriceList = sparePartsPriceRepository.saveAll(sparePartsPriceList);
                responses.setSparePartsPrices(sparePartsPriceList);
                responses = responseRepository.save(responses);
            }
            return Response.response(responses, "Response saved successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "Invalid input data", ResponseType.ERROR);
        }
    }

    @Override
    public ResponseEntity<Response<Responses>> update(ResponseDto responseDto) {
//        if (linksDto.isNotEmpty()) {
//            Optional<Provider> providerOptional = providerRepository.findById(linksDto.getProviderId());
//            if (providerOptional.isPresent()) {
//                Optional<Links> linkOptional = linksRepository.findById(linksDto.getId());
//                if (linkOptional.isPresent()) {
//                    Links link = linkOptional.get();
//                    link.setId(link.getId());
//                    link.setLink(linksDto.getLink());
//                    link.setProvider(providerOptional.get());
//                    link = linksRepository.save(link);
//                    return Response.response(link, "Link updated successfully", ResponseType.SUCCESS);
//                } else {
//                    return Response.response(null, "there is no link with this id : " + linksDto.getId(), ResponseType.NOT_FOUND);
//                }
//            } else {
//                return Response.response(null, "there is no provider with this id : " + linksDto.getProviderId(), ResponseType.NOT_FOUND);
//            }
//        } else {
//            return Response.response(null, "name is empty", ResponseType.ERROR);
//        }
        return null;
    }

    @Override
    public ResponseEntity<Response<Responses>> delete(Long id) {
        Optional<Responses> responsesOptional = responseRepository.findById(id);
        if (responsesOptional.isPresent()) {
            responseRepository.deleteById(id);
            return Response.response(null, "response deleted successfully", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "there is no response with this id : " + id, ResponseType.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Response<List<Responses>>> readAll() {
        return Response.response(responseRepository.findAll(), "All responses", ResponseType.SUCCESS);
    }

    @GetMapping("/get-all-response-of-user")
    public ResponseEntity<Response<List<Responses>>> findAllByUserId(Authentication authentication) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isPresent()) {
            return Response.response(responseRepository.findAllByUserId(userOptional.get().getId()), "All responses", ResponseType.SUCCESS);
        } else {
            return Response.response(null, "you are no authenticate", ResponseType.SUCCESS);
        }
    }


    @PostMapping("/accept-response")
    public ResponseEntity<Response<Responses>> accept(Authentication authentication, @RequestBody ResponseDto responseDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isPresent()) {
            Optional<Responses> responsesOptional = responseRepository.findById(responseDto.getId());
            if (responsesOptional.isPresent()) {
                Responses responses = responsesOptional.get();
                responses.setResponsesState(ResponsesState.ACCEPT);
                responses = responseRepository.save(responses);
                return Response.response(responses, "All responses", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is no response with this id : " + responseDto.getId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "there is no user with this email : " + authentication.getName(), ResponseType.NOT_FOUND);
        }
    }

    @PostMapping("/cancel-response")
    public ResponseEntity<Response<Responses>> cancel(Authentication authentication, @RequestBody ResponseDto responseDto) {
        Optional<User> userOptional = userRepository.findByEmail(authentication.getName());
        if (userOptional.isPresent()) {
            Optional<Responses> responsesOptional = responseRepository.findById(responseDto.getId());
            if (responsesOptional.isPresent()) {
                Responses responses = responsesOptional.get();
                responses.setResponsesState(ResponsesState.CANCEL);
                responses = responseRepository.save(responses);
                return Response.response(responses, "All responses", ResponseType.SUCCESS);
            } else {
                return Response.response(null, "there is no response with this id : " + responseDto.getId(), ResponseType.NOT_FOUND);
            }
        } else {
            return Response.response(null, "there is no user with this email : " + authentication.getName(), ResponseType.NOT_FOUND);
        }
    }
}
