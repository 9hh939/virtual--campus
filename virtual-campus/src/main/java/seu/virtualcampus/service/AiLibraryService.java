package seu.virtualcampus.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seu.virtualcampus.domain.BookCopy;
import seu.virtualcampus.domain.BookInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiLibraryService {

    @Autowired
    private BookInfoService bookInfoService;

    @Autowired
    private BookCopyService bookCopyService;

    @Autowired
    private BorrowRecordService borrowRecordService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> borrowBookByTitle(String userId, String title) {
        Map<String, Object> response = new HashMap<>();

        if (userId == null || userId.isBlank()) {
            response.put("success", false);
            response.put("message", "用户ID不能为空");
            return response;
        }
        if (title == null || title.isBlank()) {
            response.put("success", false);
            response.put("message", "书名不能为空");
            return response;
        }

        List<BookInfo> books = bookInfoService.searchBooksByTitle(title);
        if (books.isEmpty()) {
            response.put("success", false);
            response.put("message", "未找到匹配的图书");
            return response;
        }

        BookInfo targetBook = books.get(0);
        List<BookCopy> copies = bookCopyService.getCopiesByIsbn(targetBook.getIsbn());
        BookCopy availableCopy = copies.stream()
                .filter(copy -> "IN_LIBRARY".equalsIgnoreCase(copy.getStatus()))
                .findFirst()
                .orElse(null);

        if (availableCopy == null) {
            response.put("success", false);
            response.put("message", "该书当前没有可借副本，可考虑预约");
            response.put("title", targetBook.getTitle());
            response.put("isbn", targetBook.getIsbn());
            return response;
        }

        borrowRecordService.borrowBook(userId, availableCopy.getBookId());
        boolean updated = bookCopyService.borrowBook(availableCopy.getBookId());
        if (!updated) {
            throw new RuntimeException("借阅失败：副本状态更新失败");
        }
        bookInfoService.refreshBookByIsbn(targetBook.getIsbn());

        response.put("success", true);
        response.put("message", "借书成功（30天）");
        response.put("title", targetBook.getTitle());
        response.put("bookId", availableCopy.getBookId());
        response.put("isbn", targetBook.getIsbn());
        return response;
    }
}
