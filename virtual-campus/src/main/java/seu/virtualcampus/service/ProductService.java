package seu.virtualcampus.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seu.virtualcampus.domain.Product;
import seu.virtualcampus.mapper.ProductMapper;

import java.util.List;

/**
 * 商品服务类。
 * <p>
 * 提供商品的增删改查、分页、统计、过滤等相关业务逻辑。
 * </p>
 */
@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    // ========== 您要求的核心方法 ==========

    /**
     * 获取所有商品。
     *
     * @return 所有商品列表。
     */
    @Cacheable(value = "productsCache", key = "'all'")
    public List<Product> getAllProducts() {
        return productMapper.selectAll();
    }

    /**
     * 分页获取商品（可选过滤、排序）。
     *
     * @param page 页码（从1开始）。
     * @param size 每页数量。
     * @param sort 排序方式。
     * @return 分页后的商品列表。
     */
    public List<Product> getAllProducts(Integer page, Integer size, String sort) {
        return getAllProducts(page, size, sort, null, null);
    }

    /**
     * 分页获取商品（带状态与关键字过滤）。
     *
     * @param page   页码。
     * @param size   每页数量。
     * @param sort   排序方式。
     * @param status 状态过滤。
     * @param search 关键字。
     * @return 分页过滤后的商品列表。
     */
    @Cacheable(
            value = "productsCache",
            key = "'page:' + #page + ':size:' + #size + ':sort:' + (#sort == null ? '' : #sort) + ':status:' + (#status == null ? '' : #status) + ':search:' + (#search == null ? '' : #search)"
    )
    public List<Product> getAllProducts(Integer page, Integer size, String sort, String status, String search) {
        int offset = (page - 1) * size;
        String orderBy = parseSort(sort);
        return productMapper.selectPaged(offset, size, orderBy, normalizeStatus(status), normalizeSearch(search));
    }

    /**
     * 获取商品总数。
     *
     * @return 商品总数。
     */
    public long countAllProducts() {
        return productMapper.countAll();
    }

    /**
     * 统计商品数量（带过滤）。
     *
     * @param status 状态过滤。
     * @param search 关键字。
     * @return 过滤后的商品数量。
     */
    public long countProducts(String status, String search) {
        return productMapper.countByFilter(normalizeStatus(status), normalizeSearch(search));
    }

    /**
     * 解析排序参数。
     *
     * @param sort 排序参数。
     * @return 数据库字段排序字符串。
     */
    private String parseSort(String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            return "productName ASC";
        }

        java.util.Map<String, String> fieldMapping = new java.util.HashMap<>();
        fieldMapping.put("name", "productName");
        fieldMapping.put("price", "productPrice");
        fieldMapping.put("stock", "availableCount");
        fieldMapping.put("type", "productType");

        String[] parts = sort.split(",");
        if (parts.length != 2) {
            return "productName ASC";
        }

        String field = parts[0].trim();
        String direction = parts[1].trim().toUpperCase();
        String mappedField = fieldMapping.get(field);
        if (mappedField == null) {
            return "productName ASC";
        }
        if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
            direction = "ASC";
        }

        return mappedField + " " + direction;
    }

    /**
     * 规范化状态参数。
     *
     * @param status 状态字符串。
     * @return 规范化后的状态。
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return null;
        String s = status.trim().toUpperCase();
        if ("ON".equals(s) || "ACTIVE".equals(s)) return "ACTIVE";
        if ("OFF".equals(s) || "INACTIVE".equals(s)) return "INACTIVE";
        return null;
    }

    /**
     * 规范化搜索关键字。
     *
     * @param search 搜索关键字。
     * @return 规范化后的关键字。
     */
    private String normalizeSearch(String search) {
        if (search == null) return null;
        String s = search.trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 根据ID获取商品。
     *
     * @param id 商品ID。
     * @return 对应的商品对象，若不存在则返回null。
     */
    public Product getProductById(String id) {
        return productMapper.selectById(id);
    }

    /**
     * 新增商品。
     *
     * @param product 商品对象。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int addProduct(Product product) {
        if (product.getProductId() == null || product.getProductId().isEmpty()) {
            product.setProductId(generateProductId());
        }
        if (product.getStatus() == null) {
            product.setStatus("ACTIVE");
        }
        return productMapper.insert(product);
    }

    /**
     * 更新商品信息。
     *
     * @param product 商品对象。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int updateProduct(Product product) {
        return productMapper.update(product);
    }

    /**
     * 删除商品。
     *
     * @param id 商品ID。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int deleteProduct(String id) {
        return productMapper.deleteById(id);
    }

    /**
     * 修改商品状态。
     *
     * @param id     商品ID。
     * @param status 新状态。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int changeProductStatus(String id, String status) {
        return productMapper.updateStatus(id, status);
    }

    /**
     * 根据类型获取商品。
     *
     * @param productType 商品类型。
     * @return 对应类型的商品列表。
     */
    public List<Product> getProductsByType(String productType) {
        return productMapper.selectByType(productType);
    }

    /**
     * 减少库存。
     *
     * @param productId 商品ID。
     * @param quantity  减少数量。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int reduceStock(String productId, Integer quantity) {
        return productMapper.reduceStock(productId, quantity);
    }

    /**
     * 增加库存。
     *
     * @param productId 商品ID。
     * @param quantity  增加数量。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int increaseStock(String productId, Integer quantity) {
        return productMapper.increaseStock(productId, quantity);
    }

    /**
     * 根据关键字搜索商品。
     *
     * @param keyword 关键字。
     * @return 匹配的商品列表。
     */
    @Cacheable(value = "productsCache", key = "'search:' + (#keyword == null ? '' : #keyword)")
    public List<Product> searchProducts(String keyword) {
        return productMapper.searchProducts(keyword);
    }

    /**
     * 获取低库存商品。
     *
     * @param threshold 库存阈值。
     * @return 低于阈值的商品列表。
     */
    public List<Product> getLowStockProducts(Integer threshold) {
        return productMapper.selectLowStockProducts(threshold);
    }

    /**
     * 批量新增商品。
     *
     * @param products 商品列表。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int addProductsBatch(List<Product> products) {
        for (Product product : products) {
            if (product.getProductId() == null || product.getProductId().isEmpty()) {
                product.setProductId(generateProductId());
            }
            if (product.getStatus() == null) {
                product.setStatus("ACTIVE");
            }
        }
        return productMapper.insertBatch(products);
    }

    /**
     * 根据ID批量删除商品。
     *
     * @param productIds 商品ID列表。
     * @return 受影响的行数。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "productsCache", allEntries = true)
    public int removeProductsByIds(List<String> productIds) {
        return productMapper.deleteByIds(productIds);
    }

    private String generateProductId() {
        return "PROD" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }
}
