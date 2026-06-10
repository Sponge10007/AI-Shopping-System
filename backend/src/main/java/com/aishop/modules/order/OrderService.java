package com.aishop.modules.order;

import com.aishop.common.response.PageResponse;
import com.aishop.modules.order.dto.CreateOrderRequest;
import com.aishop.modules.order.dto.OrderItemResponse;
import com.aishop.modules.order.dto.OrderResponse;
import com.aishop.modules.order.dto.PayOrderRequest;
import com.aishop.modules.order.dto.PayOrderResponse;
import com.aishop.modules.order.dto.ReceiverRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OrderService {

    public OrderResponse createOrder(CreateOrderRequest request) {
        return sampleOrder("CREATED", request.receiver());
    }

    public PageResponse<OrderResponse> listOrders(String status, int page, int size) {
        List<OrderResponse> items = List.of(sampleOrder(status == null ? "CREATED" : status, sampleReceiver()));
        return PageResponse.of(items, page, size, items.size());
    }

    public OrderResponse getOrder(String orderId) {
        return sampleOrder("CREATED", sampleReceiver());
    }

    public PayOrderResponse pay(String orderId, PayOrderRequest request) {
        return new PayOrderResponse(orderId, "p10001", "PAID", "598.00");
    }

    private OrderResponse sampleOrder(String status, ReceiverRequest receiver) {
        return new OrderResponse(
                "o10001",
                "u10001",
                status,
                "598.00",
                List.of(new OrderItemResponse("10001", "蓝牙降噪耳机", "299.00", 2)),
                receiver,
                OffsetDateTime.now()
        );
    }

    private ReceiverRequest sampleReceiver() {
        return new ReceiverRequest("张三", "13800000000", "浙江省杭州市");
    }
}
