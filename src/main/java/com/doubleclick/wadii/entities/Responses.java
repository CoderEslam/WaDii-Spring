package com.doubleclick.wadii.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

//@Entity
//@Setter
//@Getter
//@Table(name = "responses")
//@AllArgsConstructor
//@NoArgsConstructor
//public class Responses {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    private String comment;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "provider_id", nullable = false)
//    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler","responses","orders","workTimes","services"})
//    private Provider provider;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "order_id", nullable = false)
//    @JsonIgnore // Prevent recursive fetching with Order
//    private Order order;
//
//    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
//    @JsonIgnoreProperties({"response", "sparePart", "hibernateLazyInitializer", "handler"})
//    private List<SparePartsPrice> sparePartsPrices;
//}

@Entity
@Setter
@Getter
@Table(name = "responses")
@AllArgsConstructor
@NoArgsConstructor
public class Responses {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnore
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<SparePartsPrice> sparePartsPrices = new ArrayList<>();

    // ✅ Custom JSON Output

    @JsonProperty("provider")
    public Object getProviderJson() {
        if (provider == null) return null;
        return new Object() {
            public final Long providerId = provider.getId();
            public final String firstName = provider.getUser().getFirstName();
            public final String lastName = provider.getUser().getLastName();
            public final String fcm = provider.getUser().getFcmToken();
            public final String phone = provider.getUser().getPhone();
            public final String image = provider.getUser().getImage();
            public final String email = provider.getUser().getEmail();
            public final Long userId = provider.getUser().getId();
        };
    }

    @JsonProperty("order")
    public Object getOrderJson() {
        if (order == null) return null;

        // Build list of spare parts with their prices
        List<Object> sparePartsList = new ArrayList<>();
        if (order.getSpareParts() != null) {
            for (SpareParts sparePart : order.getSpareParts()) {
                List<SparePartsPrice> priceEntityList = (sparePart.getSparePartsPrices() != null && !sparePart.getSparePartsPrices().isEmpty())
                        ? sparePart.getSparePartsPrices()
                        : null;

                for (SparePartsPrice sparePartsPric : priceEntityList) {
                    sparePartsList.add(new Object() {
                        public final Long id = sparePart.getId();
                        @JsonProperty("name")
                        public final String name = sparePart.getSparePartName();
                        @JsonProperty("sparePartsPrice")
                        public final Object sparePartsPrice = sparePartsPric == null ? null : new Object() {
                            public final Long id = sparePartsPric.getId();
                            public final Double price = sparePartsPric.getPrice();
                        };
                    });
                }
            }
        }

        return new Object() {
            public final Long id = order.getId();
            @JsonProperty("sparepart")
            public final List<Object> sparepart = sparePartsList;
            public final Object user = order.getUser() == null ? null : new Object() {
                public final Long id = order.getUser().getId();
            };
        };
    }
}