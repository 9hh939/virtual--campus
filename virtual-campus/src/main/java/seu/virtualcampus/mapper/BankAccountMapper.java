// BankAccountMapper.java
package seu.virtualcampus.mapper;

import org.apache.ibatis.annotations.*;
import seu.virtualcampus.domain.BankAccount;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;

/**
 * 银行账户Mapper接口。
 * <p>
 * 定义了与数据库中bank_account表相关的操作。
 */
@Mapper
public interface BankAccountMapper {

    /**
     * 根据账户号码查询账户信息。
     *
     * @param accountNumber 账户号码。
     * @return 对应的银行账户对象，如果不存在则返回null。
     */
    @Select("SELECT * FROM bank_account WHERE accountNumber = #{accountNumber}")
    BankAccount selectByAccountNumber(String accountNumber);

    /**
     * 根据用户ID查询其名下的所有银行账户。
     *
     * @param userId 用户ID。
     * @return 该用户的银行账户列表。
     */
    @Select("SELECT * FROM bank_account WHERE userId = #{userId}")
    List<BankAccount> selectByUserId(String userId);
    /**
     * 根据学号/用户ID查询银行账户
     */
    @Select("SELECT * FROM bank_account WHERE userId = #{userId}")
    BankAccount selectByUserId1(String userId);

    /**
     * 插入一个新的银行账户记录。
     *
     * @param account 要插入的银行账户对象。
     * @return 受影响的行数。
     */
    @Insert("INSERT INTO bank_account(accountNumber, userId, password, accountType, balance, status, createdDate) " +
            "VALUES(#{accountNumber}, #{userId}, #{password}, #{accountType}, #{balance}, #{status}, #{createdDate})")
    int insertAccount(BankAccount account);

    /**
     * 更新一个银行账户的完整信息。
     *
     * @param account 包含更新信息的银行账户对象。
     * @return 受影响的行数。
     */
    @Update("UPDATE bank_account SET password=#{password}, accountType=#{accountType}, " +
            "balance=#{balance}, status=#{status} WHERE accountNumber=#{accountNumber}")
    int updateAccount(BankAccount account);

    /**
     * 更新指定账户的余额。
     *
     * @param accountNumber 账户号码。
     * @param balance       新的余额。
     * @return 受影响的行数。
     */
    @Update("UPDATE bank_account SET balance = #{balance} WHERE accountNumber = #{accountNumber}")
    int updateBalance(@Param("accountNumber") String accountNumber, @Param("balance") BigDecimal balance);

    /**
     * 更新指定账户的状态。
     *
     * @param accountNumber 账户号码。
     * @param status        新的状态。
     * @return 受影响的行数。
     */
    @Update("UPDATE bank_account SET status = #{status} WHERE accountNumber = #{accountNumber}")
    int updateStatus(@Param("accountNumber") String accountNumber, @Param("status") String status);

    /**
     * 根据账户号码删除一个银行账户。
     *
     * @param accountNumber 要删除的账户号码。
     * @return 受影响的行数。
     */
    @Delete("DELETE FROM bank_account WHERE accountNumber = #{accountNumber}")
    int deleteAccount(String accountNumber);

    /**
     * 为指定账户增加余额（存款）。
     *
     * @param accountNumber 账户号码。
     * @param amount        存款金额。
     * @return 受影响的行数。
     */
    @Update("UPDATE bank_account SET balance = balance + #{amount} WHERE accountNumber = #{accountNumber}")
    int deposit(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    /**
     * 从指定账户扣除余额（取款）。
     *
     * @param accountNumber 账户号码。
     * @param amount        取款金额。
     * @return 受影响的行数。
     */
    @Update("UPDATE bank_account SET balance = balance - #{amount} WHERE accountNumber = #{accountNumber}")
    int withdraw(@Param("accountNumber") String accountNumber, @Param("amount") BigDecimal amount);

    /**
     * 查询数据库中所有的银行账户。
     *
     * @return 所有银行账户的列表。
     */
    @Select("SELECT * FROM bank_account")
    List<BankAccount> selectAllAccounts();


}