package seu.virtualcampus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import seu.virtualcampus.domain.Cart;
import seu.virtualcampus.domain.Order;
import seu.virtualcampus.domain.OrderItem;
import seu.virtualcampus.domain.Product;
import seu.virtualcampus.mapper.OrderItemMapper;
import seu.virtualcampus.mapper.OrderMapper;
import seu.virtualcampus.mapper.ProductMapper;
import seu.virtualcampus.ui.MainApp;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 订单服务类。
 * <p>
 * 提供订单的预览、创建、支付、发货、确认、取消、详情查询等相关业务逻辑，支持与银行账户模块集成。
 */
@Service
public class OrderService {
    private static final Logger logger = Logger.getLogger(OrderService.class.getName());
    // HTTP客户端用于调用银行接口
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String bankApiBaseUrl = "http://" + MainApp.host + "/api/accounts";
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderItemMapper orderItemMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CartService cartService;
    // 优先使用同进程服务调用，避免自调HTTP的不确定性；必要时仍可回退到HTTP调用
    @Autowired(required = false)
    private BankAccountService bankAccountService;
    // 商店收款账户（从配置获取）
    @Value("${shop.merchant.account:}")
    private String merchantAccount;

    @Autowired(required = false)
    private StudentInfoService studentInfoService;

    /**
     * 预览订单。
     * <p>
     * 计算购物车中商品的总价、折扣和明细，但不创建订单。
     *
     * @param userId      用户ID。
     * @param cartItemIds 购物车项ID列表，可为空。
     * @return 包含订单明细、总价、折扣等信息的结果Map。
     */
    public Map<String, Object> previewOrder(String userId, List<String> cartItemIds) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取购物车项
            List<Cart> cartItems;
            if (cartItemIds != null && !cartItemIds.isEmpty()) {
                cartItems = cartService.getCartItemsByIds(cartItemIds);
            } else {
                cartItems = cartService.getCartItemsByUserId(userId);
            }

            if (cartItems.isEmpty()) {
                result.put("success", false);
                result.put("message", "购物车为空");
                return result;
            }

            // 计算总价
            BigDecimal totalAmount = calculateTotalAmount(cartItems);

            // 检查学生折扣
            BigDecimal discountRate = getStudentDiscountRate(userId);
            BigDecimal finalAmount = totalAmount.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);

            List<Map<String, Object>> orderItems = new ArrayList<>();
            for (Cart cart : cartItems) {
                Product product = productMapper.selectById(cart.getProductId());
                if (product == null) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("productId", product.getProductId());
                item.put("productName", product.getProductName());
                item.put("quantity", cart.getQuantity());
                item.put("unitPrice", product.getProductPrice());
                item.put("subtotal", BigDecimal.valueOf(product.getProductPrice())
                        .multiply(BigDecimal.valueOf(cart.getQuantity()))
                        .setScale(2, RoundingMode.HALF_UP));
                orderItems.add(item);
            }

            result.put("success", true);
            result.put("orderItems", orderItems);
            result.put("originalAmount", totalAmount);
            result.put("discountRate", discountRate);
            result.put("finalAmount", finalAmount);
            result.put("isStudent", discountRate.compareTo(BigDecimal.ONE) < 0);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "预览订单失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 创建订单。
     *
     * @param userId      用户ID。
     * @param cartItemIds 购物车项ID列表，可为空。
     * @return 包含订单ID、金额等信息的结果Map。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(String userId, List<String> cartItemIds) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取购物车项
            List<Cart> cartItems;
            if (cartItemIds != null && !cartItemIds.isEmpty()) {
                cartItems = cartService.getCartItemsByIds(cartItemIds);
            } else {
                cartItems = cartService.getCartItemsByUserId(userId);
            }

            if (cartItems.isEmpty()) {
                result.put("success", false);
                result.put("message", "购物车为空");
                return result;
            }

            // 验证库存
            for (Cart cart : cartItems) {
                Product product = productMapper.selectById(cart.getProductId());
                if (product == null) {
                    result.put("success", false);
                    result.put("message", "商品不存在: " + cart.getProductId());
                    return result;
                }
                if (product.getAvailableCount() < cart.getQuantity()) {
                    result.put("success", false);
                    result.put("message", "商品库存不足: " + product.getProductName());
                    return result;
                }
            }

            // 计算总价
            BigDecimal totalAmount = calculateTotalAmount(cartItems);
            BigDecimal discountRate = getStudentDiscountRate(userId);
            BigDecimal finalAmount = totalAmount.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);

            // 生成订单号
            String orderId = generateOrderId();

            // 创建订单
            Order order = new Order();
            order.setOrderId(orderId);
            order.setUserId(userId);
            order.setTotalAmount(finalAmount.doubleValue());
            order.setStatus("PENDING");
            order.setOrderDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            order.setPaymentMethod("");
            order.setPaymentStatus("UNPAID");
            order.setCreatedAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            orderMapper.insert(order);

            // 创建订单项
            List<OrderItem> orderItems = new ArrayList<>();
            for (Cart cart : cartItems) {
                Product product = productMapper.selectById(cart.getProductId());

                OrderItem orderItem = new OrderItem();
                orderItem.setItemId(UUID.randomUUID().toString().replace("-", ""));
                orderItem.setOrderId(orderId);
                orderItem.setQuantity(cart.getQuantity());
                orderItem.setProductId(cart.getProductId());
                orderItem.setUnitPrice(product.getProductPrice());
                orderItem.setSubtotal(BigDecimal.valueOf(product.getProductPrice())
                        .multiply(BigDecimal.valueOf(cart.getQuantity()))
                        .multiply(discountRate)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue());

                orderItems.add(orderItem);
            }

            // 批量插入订单项
            orderItemMapper.insertBatch(orderItems);

            // 注意：不在创建订单时清空购物车，而是在支付成功后清空
            // 这样用户可以在支付前取消订单而不影响购物车

            result.put("success", true);
            result.put("orderId", orderId);
            result.put("totalAmount", finalAmount);
            result.put("message", "订单创建成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建订单失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 支付订单。
     *
     * @param userId        用户ID。
     * @param orderId       订单ID。
     * @param accountNumber 支付账户号。
     * @param password      支付账户密码。
     * @param paymentMethod 支付方式（如“立即付款”“先用后付”）。
     * @return 支付结果信息Map。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payOrder(String userId, String orderId, String accountNumber, String password, String paymentMethod) {
        Map<String, Object> result = new HashMap<>();

        logger.info("=== 开始处理支付请求 ===");
        logger.info("用户ID: " + userId);
        logger.info("订单ID: " + orderId);
        logger.info("账户号: " + accountNumber);
        logger.info("支付方式: " + paymentMethod);

        try {
            // 验证订单
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                logger.severe("错误: 订单不存在");
                result.put("success", false);
                result.put("message", "订单不存在");
                return result;
            }

            logger.info("订单信息: " + order);
            logger.info("订单状态: " + order.getStatus());
            logger.info("订单金额: " + order.getTotalAmount());

            if (!order.getUserId().equals(userId)) {
                logger.severe("错误: 用户ID不匹配 - 订单用户: " + order.getUserId() + ", 请求用户: " + userId);
                result.put("success", false);
                result.put("message", "无权限访问此订单");
                return result;
            }

            if (!"PENDING".equals(order.getStatus())) {
                logger.severe("错误: 订单状态不允许支付 - 当前状态: " + order.getStatus());
                result.put("success", false);
                result.put("message", "订单状态不允许支付");
                return result;
            }

            // 校验商家收款账户
            logger.info("商家收款账户: " + merchantAccount);
            if (merchantAccount == null || merchantAccount.isBlank()) {
                logger.severe("错误: 商家收款账户未配置");
                result.put("success", false);
                result.put("message", "商家收款账户未配置，请联系管理员设置 shop.merchant.account");
                return result;
            }

            // 计算支付金额
            BigDecimal paymentAmount = BigDecimal.valueOf(order.getTotalAmount());
            logger.info("支付金额: " + paymentAmount);

            // 先扣减库存（在同一事务内，支付失败将回滚库存变更）
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
            logger.info("订单项数量: " + orderItems.size());
            for (OrderItem item : orderItems) {
                logger.info("检查库存 - 商品ID: " + item.getProductId() + ", 需要数量: " + item.getQuantity());
                int updateResult = productMapper.reduceStock(item.getProductId(), item.getQuantity());
                if (updateResult == 0) {
                    logger.severe("错误: 库存不足 - 商品ID: " + item.getProductId());
                    result.put("success", false);
                    result.put("message", "库存不足，无法完成支付");
                    return result;
                }
                logger.info("库存扣减成功 - 商品ID: " + item.getProductId());
            }

            // 根据支付方式调用银行模块：优先使用本地服务调用，失败时回退HTTP
            logger.info("开始调用银行接口...");
            try {
                if (bankAccountService != null) {
                    if ("先用后付".equals(paymentMethod)) {
                        logger.info("[Bank] 使用本地服务调用: processPayLater");
                        bankAccountService.processPayLater(accountNumber, password, merchantAccount, paymentAmount);
                    } else {
                        logger.info("[Bank] 使用本地服务调用: processShopping");
                        bankAccountService.processShopping(accountNumber, password, merchantAccount, paymentAmount);
                    }
                } else {
                    logger.info("[Bank] 本地服务不可用，回退HTTP调用");
                    if ("先用后付".equals(paymentMethod)) {
                        logger.info("调用先用后付接口 - /api/accounts/paylater");
                        callBankApi("/paylater", accountNumber, password, merchantAccount, paymentAmount);
                    } else {
                        logger.info("调用立即付款接口 - /api/accounts/shopping");
                        callBankApi("/shopping", accountNumber, password, merchantAccount, paymentAmount);
                    }
                }
                logger.info("银行扣款处理完成");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "银行接口调用失败: " + e.getMessage(), e);
                // 回滚库存变更
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                result.put("success", false);
                result.put("message", "支付失败: " + e.getMessage());
                return result;
            }

            // 更新订单状态
            logger.info("开始更新订单状态...");
            order.setStatus("PAID");
            order.setPaymentStatus("PAID");
            order.setPaymentMethod(paymentMethod);
            order.setUpdatedAt(LocalDateTime.now());
            int updateResult = orderMapper.update(order);
            logger.info("订单状态更新结果: " + updateResult);

            // 支付成功后清空购物车
            logger.info("开始清空购物车... userId=" + userId);
            try {
                int cleared = cartService.clearUserCart(userId);
                logger.info("购物车清空成功, 受影响记录数=" + cleared);
            } catch (Exception e) {
                logger.log(Level.WARNING, "清空购物车失败: " + e.getMessage());
                // 清空购物车失败不影响支付结果，仅记录日志
                logger.log(Level.SEVERE, "支付成功但清空购物车失败: " + e.getMessage());
            }

            result.put("success", true);
            result.put("message", "支付成功");
            logger.info("=== 支付处理完成，返回成功结果 ===");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "支付处理异常: " + e.getMessage(), e);
            result.put("success", false);
            result.put("message", "支付处理失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 确认订单（发货后确认收货）。
     *
     * @param userId  用户ID。
     * @param orderId 订单ID。
     * @return 操作结果信息Map。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirmOrder(String userId, String orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                result.put("success", false);
                result.put("message", "订单不存在");
                return result;
            }

            if (!order.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限访问此订单");
                return result;
            }

            // 仅允许已发货的订单确认收货
            if (!"SHIPPED".equals(order.getStatus())) {
                result.put("success", false);
                result.put("message", "订单状态不允许确认");
                return result;
            }

            // 更新订单状态为已完成
            order.setStatus("COMPLETED");
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.update(order);

            result.put("success", true);
            result.put("message", "订单确认成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "确认订单失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 发货订单。
     *
     * @param adminId 管理员ID。
     * @param orderId 订单ID。
     * @return 操作结果信息Map。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deliverOrder(String adminId, String orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            logger.info("[OrderService] deliver start. adminId=" + adminId + ", orderId=" + orderId);
            // TODO: 校验管理员身份（后期接统一身份认证）
            // AdminService.validateAdmin(adminId);

            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                logger.warning("[OrderService] order not found: " + orderId);
                result.put("success", false);
                result.put("message", "订单不存在");
                return result;
            }

            if (!"PAID".equals(order.getStatus())) {
                logger.warning("[OrderService] invalid status for deliver. orderId=" + orderId + ", status=" + order.getStatus());
                result.put("success", false);
                result.put("message", "只能发货已支付的订单");
                return result;
            }

            // 更新订单状态为已发货
            order.setStatus("SHIPPED");
            order.setUpdatedAt(LocalDateTime.now());
            int rows = orderMapper.update(order);
            logger.info("[OrderService] update order rows: " + rows);

            result.put("success", true);
            result.put("message", "订单发货成功");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[OrderService] deliver error: " + e.getMessage(), e);
            result.put("success", false);
            result.put("message", "发货失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 取消订单。
     *
     * @param userId  用户ID。
     * @param orderId 订单ID。
     * @return 操作结果信息Map。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelOrder(String userId, String orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                result.put("success", false);
                result.put("message", "订单不存在");
                return result;
            }

            if (!order.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限访问此订单");
                return result;
            }

            if (!"PENDING".equals(order.getStatus())) {
                result.put("success", false);
                result.put("message", "只能取消未支付的订单");
                return result;
            }

            // 更新订单状态为已取消
            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.update(order);

            result.put("success", true);
            result.put("message", "订单取消成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "取消订单失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取订单详情。
     *
     * @param userId  用户ID。
     * @param orderId 订单ID。
     * @return 包含订单及订单项详细信息的结果Map。
     */
    public Map<String, Object> getOrderDetail(String userId, String orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                result.put("success", false);
                result.put("message", "订单不存在");
                return result;
            }

            if (!order.getUserId().equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限访问此订单");
                return result;
            }

            // 获取订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(orderId);
            List<Map<String, Object>> itemDetails = new ArrayList<>();

            for (OrderItem item : orderItems) {
                Product product = productMapper.selectById(item.getProductId());
                Map<String, Object> itemDetail = new HashMap<>();
                itemDetail.put("itemId", item.getItemId());
                itemDetail.put("productId", item.getProductId());
                itemDetail.put("productName", product != null ? product.getProductName() : "商品已下架");
                itemDetail.put("quantity", item.getQuantity());
                itemDetail.put("unitPrice", item.getUnitPrice());
                itemDetail.put("subtotal", item.getSubtotal());
                itemDetails.add(itemDetail);
            }

            result.put("success", true);
            result.put("order", order);
            result.put("orderItems", itemDetails);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取订单详情失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 调用银行账户API接口。
     *
     * @param endpoint    API接口路径。
     * @param fromAccount 支付账户号。
     * @param password    支付账户密码。
     * @param toAccount   商家账户号。
     * @param amount      支付金额。
     * @throws IOException 调用银行API失败时抛出。
     */
    private void callBankApi(String endpoint, String fromAccount, String password, String toAccount, BigDecimal amount) throws IOException {
        String url = bankApiBaseUrl + endpoint + "?fromAccount=" + fromAccount
                + "&password=" + password + "&toAccount=" + toAccount + "&amount=" + amount;

        logger.info("发起银行API请求: " + url);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            logger.info("银行API响应: " + response.code() + " - " + responseBody);

            if (!response.isSuccessful()) {
                throw new RuntimeException("银行API调用失败: " + responseBody);
            }
        }
    }

    // ========== 原有方法保留 ==========

    /**
     * 创建订单（原有方法，建议使用新版）。
     *
     * @param order 订单对象。
     * @return 插入结果。
     */
    public int createOrder(Order order) {
        return orderMapper.insert(order);
    }

    /**
     * 取消订单（原有方法，建议使用新版）。
     *
     * @param orderId 订单ID。
     * @return 操作影响的行数。
     */
    public int cancelOrder(String orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            return orderMapper.update(order);
        }
        return 0;
    }

    /**
     * 更新订单信息。
     *
     * @param order 订单对象。
     * @return 操作影响的行数。
     */
    public int updateOrder(Order order) {
        order.setUpdatedAt(LocalDateTime.now());
        return orderMapper.update(order);
    }

    /**
     * 根据ID获取订单。
     *
     * @param orderId 订单ID。
     * @return 对应的订单对象，若不存在则返回null。
     */
    public Order getOrderById(String orderId) {
        return orderMapper.selectById(orderId);
    }

    /**
     * 根据用户ID获取所有订单。
     *
     * @param userId 用户ID。
     * @return 该用户的所有订单列表。
     */
    public List<Order> getOrdersByUserId(String userId) {
        return orderMapper.selectByUserId(userId);
    }

    /**
     * 获取所有订单。
     *
     * @return 所有订单列表。
     */
    public List<Order> getAllOrders() {
        return orderMapper.selectAll();
    }

    /**
     * 更新订单状态。
     *
     * @param orderId       订单ID。
     * @param status        新状态。
     * @param paymentStatus 支付状态。
     * @return 操作影响的行数。
     */
    public int updateOrderStatus(String orderId, String status, String paymentStatus) {
        return orderMapper.updateStatus(orderId, status, paymentStatus);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 计算购物车商品总价。
     *
     * @param cartItems 购物车项列表。
     * @return 总价。
     */
    private BigDecimal calculateTotalAmount(List<Cart> cartItems) {
        BigDecimal total = BigDecimal.ZERO;

        for (Cart cart : cartItems) {
            Product product = productMapper.selectById(cart.getProductId());
            if (product != null) {
                BigDecimal itemTotal = BigDecimal.valueOf(product.getProductPrice())
                        .multiply(BigDecimal.valueOf(cart.getQuantity()));
                total = total.add(itemTotal);
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取学生折扣率。
     *
     * @param userId 用户ID。
     * @return 折扣率（如1为无折扣）。
     */
    private BigDecimal getStudentDiscountRate(String userId) {
        try {
            if (studentInfoService != null) {
                // 假设 userId 可以转换为 studentId，或者需要额外的映射逻辑
                // 这里简化处理，实际项目中需要建立用户和学生的关联关系
                // StudentInfo student = studentInfoService.getStudentInfo(Long.valueOf(userId));
                // if (student != null && "ACTIVE".equals(student.getStatus())) {
                //     return BigDecimal.valueOf(0.95); // 95折
                // }

                // 由于当前 StudentInfo 没有 status 字段，暂时返回原价
                return BigDecimal.ONE;
            }
        } catch (Exception e) {
            // 如果学生服务不可用，按原价计算
        }

        return BigDecimal.ONE; // 原价，无折扣
    }

    /**
     * 生成订单ID。
     *
     * @return 新订单ID。
     */
    private String generateOrderId() {
        return "ORDER" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
}
