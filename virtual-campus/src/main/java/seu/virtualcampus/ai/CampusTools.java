package seu.virtualcampus.ai;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CampusTools {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // @Tool 注解里的文字就是写给大模型看的“说明书”，它会根据这句话决定要不要调这个方法
    @Tool("当用户询问某本书的信息、作者、出版社时调用。参数是书名包含的关键字。")
    public String searchBook(String titleKeyword) {
        System.out.println("🤖 [系统日志] AI 触发了本地工具 searchBook, 搜索关键字: " + titleKeyword);

        // 查 book_info 表
        String sql = "SELECT title, author, publisher FROM book_info WHERE title LIKE ?";
        List<Map<String, Object>> books = jdbcTemplate.queryForList(sql, "%" + titleKeyword + "%");

        if (books.isEmpty()) {
            return "数据库中没有找到包含该关键字的书籍。";
        }
        return "查询到的书籍数据：" + books.toString();
    }

    @Tool("当用户询问课程信息、授课教师、上课时间或地点、课程学分时调用。参数为课程名称或教师姓名的关键字。")
    public String searchCourse(String keyword) {
        System.out.println("🤖 [系统日志] AI 触发了本地工具 searchCourse, 搜索关键字: " + keyword);

        // 查 course 表，支持按课程名或老师名模糊搜索
        String sql = "SELECT courseName, courseTeacher, courseCredit, courseTime, courseLocation, courseCapacity, coursePeopleNumber " +
                "FROM course WHERE courseName LIKE ? OR courseTeacher LIKE ?";
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(sql, "%" + keyword + "%", "%" + keyword + "%");

        if (courses.isEmpty()) {
            return "数据库中没有找到包含该关键字的课程。";
        }
        return "查询到的课程数据：" + courses.toString();
    }

    @Tool("当用户询问校园商店某个商品的价格、库存时调用。参数是商品名称关键字。")
    public String searchProduct(String productName) {
        System.out.println("🤖 [系统日志] AI 触发了本地工具 searchProduct, 搜索关键字: " + productName);

        // 查 product 表
        String sql = "SELECT productName, productPrice, availableCount FROM product WHERE productName LIKE ?";
        List<Map<String, Object>> products = jdbcTemplate.queryForList(sql, "%" + productName + "%");

        if (products.isEmpty()) {
            return "校园商店没有售卖该商品。";
        }
        return "查询到的商品数据：" + products.toString();
    }

    @Tool("当用户询问自己银行卡余额、我的钱、账户余额时调用。必须将当前用户的ID作为参数传入。")
    public String checkMyBalance(Integer userId) {
        System.out.println("🤖 [鉴权日志] AI 尝试查询私有数据 -> checkMyBalance, 当前操作用户ID: " + userId);

        // 查 bank_account 表（注意表里的 userId 字段设计的是 TEXT 类型，所以要转 String）
        String sql = "SELECT accountType, Balance, status FROM bank_account WHERE userId = ?";
        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(sql, String.valueOf(userId));

        if (accounts.isEmpty()) {
            return "该用户尚未开通虚拟银行账户。";
        }
        return "查询到的私人账户数据：" + accounts.toString();
    }

    @Tool("当用户询问自己借了什么书、我的借阅记录、有没有书没还时调用。必须将当前用户的ID作为参数传入。")
    public String checkMyBorrowRecords(Integer userId) {
        System.out.println("🤖 [鉴权日志] AI 尝试查询私有数据 -> checkMyBorrowRecords, 当前操作用户ID: " + userId);

        // 联表查询借阅记录和书名
        String sql = "SELECT b.title, r.borrowDate, r.dueDate, r.status " +
                "FROM borrow_records r JOIN book_copy c ON r.bookId = c.bookId " +
                "JOIN book_info b ON c.isbn = b.isbn " +
                "WHERE r.userId = ?";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, String.valueOf(userId));

        if (records.isEmpty()) {
            return "该用户目前没有任何借阅记录。";
        }
        return "查询到的私人借阅记录：" + records.toString();
    }

    @Tool("当用户询问商店里最便宜的商品、最贵的商品、或者随便看看有什么推荐时调用。参数 sortDirection 传入 'ASC' 代表查最便宜，'DESC' 代表查最贵。参数 limit 传入想要查询的数量，通常填 3 或 5。")
    public String getProductsByPrice(String sortDirection, int limit) {
        System.out.println("🤖 [系统日志] AI 触发了聚合查询 -> getProductsByPrice, 排序: " + sortDirection + ", 数量: " + limit);

        // 限制最大查询数量，防止数据量过大撑爆 AI 上下文
        int actualLimit = (limit > 0 && limit <= 10) ? limit : 5;
        // 手动控制排序方向，防止 SQL 注入
        String order = "DESC".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";

        // 查询 product 表，按价格排序
        String sql = "SELECT productName, productPrice, availableCount FROM product WHERE status = 'ACTIVE' ORDER BY productPrice " + order + " LIMIT ?";
        List<Map<String, Object>> products = jdbcTemplate.queryForList(sql, actualLimit);

        if (products.isEmpty()) {
            return "校园商店目前没有上架任何商品。";
        }
        return "查询到的商品数据（已按价格 " + order + " 排序）：" + products.toString();
    }
    @Tool("当用户要求将某件商品加入购物车、或者说'帮我买xx'时调用。必须传入当前用户的ID、商品名称关键字、以及购买数量(如果用户没说数量，默认为1)。")
    public String addProductToCart(Integer userId, String productName, int quantity) {
        System.out.println("🤖 [行动日志] AI 尝试修改数据库 -> addProductToCart, 用户: " + userId + ", 商品: " + productName + ", 数量: " + quantity);

        // 1. 模糊搜索商品，获取确切的 productId 和库存
        String searchSql = "SELECT productId, productName, productPrice, availableCount FROM product WHERE productName LIKE ? AND status = 'ACTIVE' LIMIT 1";
        List<Map<String, Object>> products = jdbcTemplate.queryForList(searchSql, "%" + productName + "%");

        if (products.isEmpty()) {
            return "加入失败：商店里没有找到名字包含 '" + productName + "' 的商品。请让用户换个词试试。";
        }

        Map<String, Object> product = products.get(0);
        String productId = (String) product.get("productId");
        String actualName = (String) product.get("productName");
        int stock = (int) product.get("availableCount");

        // 2. 核心业务校验：库存检查
        if (stock < quantity) {
            return "加入失败：商品 '" + actualName + "' 库存不足，当前仅剩 " + stock + " 件。";
        }

        // 3. 执行写库操作（插入购物车表）
        // 这里的写操作在真实的复杂业务中应该调用加了 @Transactional 的 CartService，这里直接操作 DB 做演示
        String cartItemId = java.util.UUID.randomUUID().toString();
        String insertSql = "INSERT INTO cart (cartItemId, userId, productId, quantity, isActive) VALUES (?, ?, ?, ?, 1)";

        // 注意：数据库中 userId 是 TEXT 类型，所以需要 String.valueOf 转换
        jdbcTemplate.update(insertSql, cartItemId, String.valueOf(userId), productId, quantity);

        // 4. 将成功的结果和情绪指示返回给大模型
        return "操作成功！已将 " + quantity + " 件 '" + actualName + "' 成功加入用户的购物车。请用俏皮、邀功的语气告诉用户已经加好啦，并提醒他们可以去购物车页面查看。";
    }
}