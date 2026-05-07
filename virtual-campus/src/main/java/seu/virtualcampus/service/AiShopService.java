package seu.virtualcampus.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seu.virtualcampus.domain.Product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiShopService {

    @Autowired
    private ProductService productService;

    @Autowired
    private CartService cartService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addToCartByKeyword(String userId, String keyword, int quantity) {
        Map<String, Object> response = new HashMap<>();

        if (userId == null || userId.isBlank()) {
            response.put("success", false);
            response.put("message", "用户ID不能为空");
            return response;
        }
        if (keyword == null || keyword.isBlank()) {
            response.put("success", false);
            response.put("message", "商品关键词不能为空");
            return response;
        }
        if (quantity <= 0) {
            response.put("success", false);
            response.put("message", "商品数量必须大于0");
            return response;
        }

        List<Product> products = productService.searchProducts(keyword);
        Product targetProduct = products.stream()
                .filter(product -> "ACTIVE".equals(product.getStatus()))
                .findFirst()
                .orElse(null);

        if (targetProduct == null) {
            response.put("success", false);
            response.put("message", "未找到可加入购物车的匹配商品");
            return response;
        }

        int result = cartService.addItem(userId, targetProduct.getProductId(), quantity);
        if (result <= 0) {
            response.put("success", false);
            response.put("message", "加入购物车失败");
            return response;
        }

        response.put("success", true);
        response.put("message", "商品添加到购物车成功");
        response.put("productId", targetProduct.getProductId());
        response.put("productName", targetProduct.getProductName());
        response.put("quantity", quantity);
        return response;
    }
}
