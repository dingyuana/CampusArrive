package com.campusarrive.integration.cdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 主数据映射服务 — 系统标识符翻译中枢。
 *
 * <p>规格来源：SIM-CA-2026-08 第 6.1 节主数据映射、MW-2.3 Debezium CDC。
 *
 * 迎新系统涉及多套标识符体系，需在 CDC 数据同步时进行翻译：
 * <ul>
 *   <li>student_id — 学号（报到系统主键）</li>
 *   <li>id_card — 身份证号（教务/公安系统标识）</li>
 *   <li>card_id — 一卡通卡号（一卡通系统标识）</li>
 * </ul>
 *
 * <p>映射关系示例：
 * <pre>
 *   student_id=20260001 ↔ id_card=330***********1234 ↔ card_id=CARD20260001
 * </pre></p>
 *
 * <p>当前实现为内存版（ConcurrentHashMap），生产环境应替换为 MySQL id_mapping 表实现，
 * 映射关系在学生报到时由 checkin-service 写入，本服务读取使用。</p>
 */
public class DataMappingService {

    private static final Logger log = LoggerFactory.getLogger(DataMappingService.class);

    /** studentId → 映射条目 */
    private final ConcurrentMap<String, MappingEntry> byStudentId = new ConcurrentHashMap<>();

    /** idCard → studentId（反向索引） */
    private final ConcurrentMap<String, String> byIdCard = new ConcurrentHashMap<>();

    /** cardId → studentId（反向索引） */
    private final ConcurrentMap<String, String> byCardId = new ConcurrentHashMap<>();

    /**
     * 学号 → 身份证号映射。
     *
     * @param studentId 学号
     * @return 身份证号，映射不存在时返回 {@link Optional#empty()}
     */
    public Optional<String> mapStudentIdToIdCard(String studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        MappingEntry entry = byStudentId.get(studentId);
        return entry != null ? Optional.ofNullable(entry.idCard()) : Optional.empty();
    }

    /**
     * 身份证号 → 学号映射。
     *
     * @param idCard 身份证号
     * @return 学号，映射不存在时返回 {@link Optional#empty()}
     */
    public Optional<String> mapIdCardToStudentId(String idCard) {
        if (idCard == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIdCard.get(idCard));
    }

    /**
     * 一卡通卡号 → 学号映射。
     *
     * @param cardId 一卡通卡号
     * @return 学号，映射不存在时返回 {@link Optional#empty()}
     */
    public Optional<String> mapCardId(String cardId) {
        if (cardId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCardId.get(cardId));
    }

    /**
     * 获取学号对应的完整映射条目（含 id_card 和 card_id）。
     *
     * @param studentId 学号
     * @return 映射条目，不存在时返回 {@link Optional#empty()}
     */
    public Optional<MappingEntry> getMapping(String studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byStudentId.get(studentId));
    }

    /**
     * 创建映射关系。
     *
     * <p>在学生报到时调用，建立学号 ↔ 身份证号 ↔ 一卡通卡号的三方映射。
     * 已存在的映射会被覆盖（支持更新）。</p>
     *
     * @param studentId 学号
     * @param idCard    身份证号（可为 null）
     * @param cardId    一卡通卡号（可为 null）
     */
    public void createMapping(String studentId, String idCard, String cardId) {
        MappingEntry entry = new MappingEntry(studentId, idCard, cardId);
        byStudentId.put(studentId, entry);
        if (idCard != null) {
            byIdCard.put(idCard, studentId);
        }
        if (cardId != null) {
            byCardId.put(cardId, studentId);
        }
        log.info("[DataMappingService] 创建映射: studentId={}, idCard={}, cardId={}",
                studentId,
                idCard != null ? idCard.replaceAll("(\\d{3})\\d*(\\d{4})", "$1***********$2") : null,
                cardId);
    }

    /**
     * 移除映射关系（测试用）。
     *
     * @param studentId 学号
     */
    public void removeMapping(String studentId) {
        MappingEntry entry = byStudentId.remove(studentId);
        if (entry != null) {
            if (entry.idCard() != null) {
                byIdCard.remove(entry.idCard());
            }
            if (entry.cardId() != null) {
                byCardId.remove(entry.cardId());
            }
        }
    }

    /**
     * 获取当前映射数量（测试用）。
     *
     * @return 映射条目数
     */
    public int size() {
        return byStudentId.size();
    }

    /**
     * 主数据映射条目。
     *
     * @param studentId 学号
     * @param idCard    身份证号
     * @param cardId    一卡通卡号
     */
    public record MappingEntry(String studentId, String idCard, String cardId) {
    }
}
