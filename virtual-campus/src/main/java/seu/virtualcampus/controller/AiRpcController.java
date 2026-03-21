package seu.virtualcampus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import seu.virtualcampus.domain.BankAccount;
import seu.virtualcampus.domain.Product;
import seu.virtualcampus.service.BankAccountService;
import seu.virtualcampus.service.ProductService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 这是一个专门为 Python AI 大脑提供的内部 RPC 接口（微服务通信基石）
 */
@RestController
@RequestMapping("/api/ai-rpc")
public class AiRpcController {

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private ProductService productService;

    // 给 Python 查询余额用的接口
    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 这里调用你原本写好的 Service
            BankAccount account = bankAccountService.getAccountByUserId(userId);
            if (account != null) {
                response.put("success", true);
                response.put("balance", account.getBalance());
            } else {
                response.put("success", false);
                response.put("message", "未找到该用户的银行账户");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    // 给 Python 查询商品用的接口
    @GetMapping("/products")
    public Map<String, Object> searchProducts(@RequestParam String keyword) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 调用你原本的 Service 获取所有商品
            List<Product> allProducts = productService.getAllProducts();

            // 过滤出名字包含 keyword 且状态为 ACTIVE 的商品
            List<String> matchedProducts = allProducts.stream()
                    .filter(p -> p.getProductName().contains(keyword) && "ACTIVE".equals(p.getStatus()))
                    .map(p -> p.getProductName() + "(价格:" + p.getProductPrice() + "元, 库存:" + p.getAvailableCount() + "件)")
                    .collect(Collectors.toList());

            if (!matchedProducts.isEmpty()) {
                response.put("success", true);
                response.put("products", matchedProducts);
            } else {
                response.put("success", false);
            }
        } catch (Exception e) {
            response.put("success", false);
        }
        return response;
    }
}