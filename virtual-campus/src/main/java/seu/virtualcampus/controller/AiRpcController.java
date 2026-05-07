package seu.virtualcampus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import seu.virtualcampus.domain.BankAccount;
import seu.virtualcampus.domain.Product;
import seu.virtualcampus.domain.Transaction;
import seu.virtualcampus.service.AiLibraryService;
import seu.virtualcampus.service.AiShopService;
import seu.virtualcampus.service.BankAccountService;
import seu.virtualcampus.service.ProductService;

import java.time.LocalDateTime;
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

    @Autowired
    private AiLibraryService aiLibraryService;

    @Autowired
    private AiShopService aiShopService;

    @GetMapping("/balance")
    public Map<String, Object> getBalance(@RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();
        try {
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

    @GetMapping("/account-info")
    public Map<String, Object> getAccountInfo(@RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            BankAccount account = bankAccountService.getAccountByUserId(userId);
            response.put("success", true);
            response.put("accountNumber", account.getAccountNumber());
            response.put("accountType", account.getAccountType());
            response.put("status", account.getStatus());
            response.put("balance", account.getBalance());
            response.put("createdDate", account.getCreatedDate());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping("/recent-transactions")
    public Map<String, Object> getRecentTransactions(@RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            BankAccount account = bankAccountService.getAccountByUserId(userId);
            List<String> transactions = bankAccountService
                    .getTransactionHistory(account.getAccountNumber(), LocalDateTime.now().minusDays(30), LocalDateTime.now())
                    .stream()
                    .limit(5)
                    .map(t -> formatTransaction(t, account.getAccountNumber()))
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("transactions", transactions);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    @PostMapping("/library/borrow-by-title")
    public ResponseEntity<Map<String, Object>> borrowBookByTitle(@RequestParam String userId,
                                                                 @RequestParam String title) {
        try {
            Map<String, Object> response = aiLibraryService.borrowBookByTitle(userId, title);
            if (Boolean.TRUE.equals(response.get("success"))) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/shop/add-to-cart")
    public ResponseEntity<Map<String, Object>> addToCartByKeyword(@RequestParam String userId,
                                                                  @RequestParam String keyword,
                                                                  @RequestParam int quantity) {
        try {
            Map<String, Object> response = aiShopService.addToCartByKeyword(userId, keyword, quantity);
            if (Boolean.TRUE.equals(response.get("success"))) {
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/products")
    public Map<String, Object> searchProducts(@RequestParam String keyword) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Product> allProducts = productService.getAllProducts();

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

    private String formatTransaction(Transaction transaction, String currentAccountNumber) {
        String type = transaction.getTransactionType();
        String amount = String.valueOf(transaction.getAmount());
        String time = String.valueOf(transaction.getTransactionTime());
        String remark = transaction.getRemark();

        if ("TRANSFER".equals(type)) {
            if (currentAccountNumber.equals(transaction.getFromAccountNumber())) {
                return "转出: 金额=" + amount + ", 时间=" + time + ", 备注=" + remark;
            }
            return "转入: 金额=" + amount + ", 时间=" + time + ", 备注=" + remark;
        }

        return type + ": 金额=" + amount + ", 时间=" + time + ", 备注=" + remark;
    }
}
