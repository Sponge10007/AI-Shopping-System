package com.aishop.modules.order;

import com.aishop.common.exception.BusinessException;
import com.aishop.common.exception.ErrorCode;
import com.aishop.common.response.PageResponse;
import com.aishop.common.security.CurrentUser;
import com.aishop.infrastructure.persistence.entity.OrderEntity;
import com.aishop.infrastructure.persistence.entity.OrderItemEntity;
import com.aishop.infrastructure.persistence.entity.PaymentEntity;
import com.aishop.infrastructure.persistence.entity.ProductEntity;
import com.aishop.infrastructure.persistence.repository.OrderItemRepository;
import com.aishop.infrastructure.persistence.repository.OrderRepository;
import com.aishop.infrastructure.persistence.repository.PaymentRepository;
import com.aishop.infrastructure.persistence.repository.ProductRepository;
import com.aishop.modules.order.dto.CreateOrderItemRequest;
import com.aishop.modules.order.dto.CreateOrderRequest;
import com.aishop.modules.order.dto.OrderItemResponse;
import com.aishop.modules.order.dto.OrderResponse;
import com.aishop.modules.order.dto.PayOrderRequest;
import com.aishop.modules.order.dto.PayOrderResponse;
import com.aishop.modules.order.dto.ReceiverRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 订单服务 — 处理订单的创建、查询、支付
 *
 * 技术要点：
 * 1. orderId 生成规则：o + 数字（如 o10001），启动时从数据库最大ID初始化
 * 2. paymentId 生成规则：pay + 数字（如 pay10001），启动时从数据库最大ID初始化
 * 3. 创建订单时使用事务保证原子性：扣库存 + 创建订单 + 创建订单项
 * 4. 商品信息使用快照（名称、价格），防止商家修改后影响历史订单
 * 5. 支付时创建支付记录，更新订单状态
 * 6. 库存扣减使用原子操作防止并发超卖
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;

    /**
     * 自增 ID 生成器，启动时从数据库最大 ID 初始化
     */
    private final AtomicLong orderIdCounter = new AtomicLong(10001);
    private final AtomicLong paymentIdCounter = new AtomicLong(10001);

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PaymentRepository paymentRepository,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productRepository = productRepository;
    }

    /**
     * 启动时从数据库读取最大 orderId 数字部分，初始化计数器
     */
    @PostConstruct
    public void initCounters() {
        // 初始化 orderId 计数器
        try {
            Long maxOrderId = orderRepository.findMaxOrderIdNumeric();
            if (maxOrderId != null && maxOrderId > 0) {
                orderIdCounter.set(maxOrderId + 1);
                log.info("订单ID计数器已从数据库初始化: maxId={}, nextId=o{}", maxOrderId, maxOrderId + 1);
            } else {
                log.info("订单ID计数器使用默认初始值: o10001");
            }
        } catch (Exception e) {
            log.warn("初始化订单ID计数器失败，使用默认值: {}", e.getMessage());
        }

        // 初始化 paymentId 计数器
        try {
            Long maxPaymentId = paymentRepository.findMaxPaymentIdNumeric();
            if (maxPaymentId != null && maxPaymentId > 0) {
                paymentIdCounter.set(maxPaymentId + 1);
                log.info("支付ID计数器已从数据库初始化: maxId={}, nextId=pay{}", maxPaymentId, maxPaymentId + 1);
            } else {
                log.info("支付ID计数器使用默认初始值: pay10001");
            }
        } catch (Exception e) {
            log.warn("初始化支付ID计数器失败，使用默认值: {}", e.getMessage());
        }
    }

    /**
     * 创建订单
     *
     * 业务流程：
     * 1. 校验参数（商品列表不为空、数量合法、收货信息完整）
     * 2. 遍历商品列表，逐个校验商品存在、在售、库存充足
     * 3. 原子扣减库存
     * 4. 创建订单项（快照商品名称和价格）
     * 5. 计算总金额
     * 6. 创建订单
     * 7. 返回订单响应
     *
     * 事务保证：整个操作在一个事务中完成，任一环节失败则全部回滚
     */
    @Transactional
    public OrderResponse createOrder(CurrentUser currentUser, CreateOrderRequest request) {
        // 1. 参数校验
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "订单商品列表不能为空");
        }
        if (request.receiver() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "收货信息不能为空");
        }

        // 2. 生成 orderId
        String orderId;
        synchronized (orderIdCounter) {
            orderId = "o" + orderIdCounter.getAndIncrement();
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<OrderItemEntity> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 3. 遍历商品，校验并扣库存
        for (CreateOrderItemRequest item : request.items()) {
            // 3.1 查询商品
            ProductEntity product = productRepository.findByProductId(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "商品不存在: " + item.productId()));

            // 3.2 校验商品状态
            if (!product.isOnSale()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        "商品已下架: " + product.getName());
            }

            // 3.3 校验库存
            if (!product.hasSufficientStock(item.quantity())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        "商品库存不足: " + product.getName() + "，当前库存: " + product.getStock());
            }

            // 3.4 原子扣减库存
            int affected = productRepository.decreaseStock(
                    item.productId(), item.quantity(), now);
            if (affected == 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        "商品库存不足（并发扣减）: " + product.getName());
            }

            // 3.5 创建订单项（快照）
            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrderId(orderId);
            orderItem.setProductId(item.productId());
            orderItem.setProductName(product.getName());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setQuantity(item.quantity());
            orderItems.add(orderItem);

            // 3.6 累加总金额
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.quantity()));
            totalAmount = totalAmount.add(subtotal);
        }

        // 4. 创建订单
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setUserId(currentUser.userId());
        order.setStatus("CREATED");
        order.setTotalAmount(totalAmount);
        order.setReceiverName(request.receiver().name());
        order.setReceiverPhone(request.receiver().phone());
        order.setReceiverAddress(request.receiver().address());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        // 5. 保存订单项
        for (OrderItemEntity item : orderItems) {
            item.setOrderId(orderId);
            orderItemRepository.save(item);
        }

        log.info("订单创建成功: orderId={}, userId={}, totalAmount={}, items={}",
                orderId, currentUser.userId(), totalAmount, orderItems.size());

        // 6. 构建响应
        return toOrderResponse(order, orderItems);
    }

    /**
     * 查询当前用户的订单列表
     */
    public PageResponse<OrderResponse> listOrders(CurrentUser currentUser, String status, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<OrderEntity> orderPage;
        if (status != null && !status.isBlank()) {
            orderPage = orderRepository.findByUserIdAndStatus(currentUser.userId(), status, pageable);
        } else {
            orderPage = orderRepository.findByUserId(currentUser.userId(), pageable);
        }

        List<OrderResponse> items = orderPage.getContent().stream()
                .map(order -> {
                    List<OrderItemEntity> orderItems = orderItemRepository.findByOrderId(order.getOrderId());
                    return toOrderResponse(order, orderItems);
                })
                .collect(Collectors.toList());

        return PageResponse.of(items, page, size, orderPage.getTotalElements());
    }

    /**
     * 获取订单详情
     */
    public OrderResponse getOrder(CurrentUser currentUser, String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);

        // 校验权限：只能查看自己的订单
        if (!order.getUserId().equals(currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看此订单");
        }

        List<OrderItemEntity> orderItems = orderItemRepository.findByOrderId(orderId);
        return toOrderResponse(order, orderItems);
    }

    /**
     * 支付订单
     *
     * 业务流程：
     * 1. 校验订单存在
     * 2. 校验订单属于当前用户
     * 3. 校验订单状态为 CREATED（待支付）
     * 4. 创建支付记录
     * 5. 更新订单状态为 PAID
     */
    @Transactional
    public PayOrderResponse pay(CurrentUser currentUser, String orderId, PayOrderRequest request) {
        OrderEntity order = findOrderOrThrow(orderId);

        // 校验权限
        if (!order.getUserId().equals(currentUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权支付此订单");
        }

        // 校验订单状态
        if (!order.isPayable()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "订单状态不允许支付，当前状态: " + order.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 生成 paymentId
        String paymentId;
        synchronized (paymentIdCounter) {
            paymentId = "pay" + paymentIdCounter.getAndIncrement();
        }

        // 创建支付记录
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentId(paymentId);
        payment.setOrderId(orderId);
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(request.method() != null ? request.method() : "BALANCE");
        payment.setStatus("SUCCESS");
        payment.setCreatedAt(now);
        paymentRepository.save(payment);

        // 更新订单状态
        order.setStatus("PAID");
        order.setUpdatedAt(now);
        orderRepository.save(order);

        log.info("订单支付成功: orderId={}, paymentId={}, amount={}, method={}",
                orderId, paymentId, order.getTotalAmount(), payment.getMethod());

        return new PayOrderResponse(orderId, paymentId, "PAID", order.getTotalAmount().toPlainString());
    }

    // ==================== 私有方法 ====================

    /**
     * 根据 orderId 查找订单，不存在则抛出异常
     */
    private OrderEntity findOrderOrThrow(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "订单不存在: " + orderId));
    }

    /**
     * 将 OrderEntity + OrderItemEntity 转换为 OrderResponse
     */
    private OrderResponse toOrderResponse(OrderEntity order, List<OrderItemEntity> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice().toPlainString(),
                        item.getQuantity()
                ))
                .collect(Collectors.toList());

        ReceiverRequest receiver = new ReceiverRequest(
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress()
        );

        return new OrderResponse(
                order.getOrderId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount().toPlainString(),
                itemResponses,
                receiver,
                order.getCreatedAt()
        );
    }
}
