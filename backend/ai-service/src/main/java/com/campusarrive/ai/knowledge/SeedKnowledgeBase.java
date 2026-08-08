package com.campusarrive.ai.knowledge;

import java.time.Instant;
import java.util.Map;

/**
 * 种子知识库数据加载器。
 *
 * <p>规格来源：FR-01-07 / FR-01-08 / FR-01-09 / FR-01-10 —
 * 将报到流程手册、校园 POI、FAQ、材料清单模板四类知识结构化入库。</p>
 *
 * <p>本加载器提供 v1.1 迎新季的初始知识库数据，作为 AI-3.1 检索验证的测试基线，
 * 也是 MaxKB 知识库初始化的内容来源（经辅导员复核确认口径后正式生效）。
 * 所有入库内容均不含明文 PII（AID 5.2 / 8.3）。</p>
 */
public final class SeedKnowledgeBase {

    /** 流程手册文档 ID（与 API 5.1 sources.doc_id 契约一致）。 */
    public static final String DOC_PROCESS = "kb-manual-2026-v3";
    /** POI 文档 ID。 */
    public static final String DOC_POI = "kb-poi-2026-v1";
    /** FAQ 文档 ID。 */
    public static final String DOC_FAQ = "kb-faq-2026-v1";
    /** 材料清单文档 ID。 */
    public static final String DOC_MATERIAL = "kb-material-2026-v1";

    private SeedKnowledgeBase() {
    }

    /** 加载全部四类种子知识库。 */
    public static void loadAll(InMemoryKnowledgeStore store) {
        loadProcessManual(store);
        loadPoi(store);
        loadFaq(store);
        loadMaterial(store);
    }

    /** 加载报到流程手册（FR-01-07）。 */
    public static void loadProcessManual(InMemoryKnowledgeStore store) {
        KnowledgeDocument doc = new KnowledgeDocument(
                DOC_PROCESS, "2026级新生报到手册", KnowledgeCategory.PROCESS,
                "学生处", "v3", Instant.parse("2026-08-01T00:00:00Z"));
        ingest(store, doc, "环节一 身份核验",
                "身份核验为报到第一步。新生携带录取通知书、身份证原件至行政楼一楼核验窗口，"
                        + "由工作人员核验身份并领取报到流程单。核验时间 8:00-17:00。");
        ingest(store, doc, "环节二 缴纳学费",
                "身份核验通过后，前往财务处一楼大厅缴纳学费。支持微信、支付宝、银行卡三种方式。"
                        + "缴费后凭缴费凭证在流程单盖章。财务处工作时间 8:30-16:30。");
        ingest(store, doc, "环节三 宿舍入住",
                "凭缴费盖章的流程单至所在楼栋宿管处办理入住，领取宿舍钥匙。需出示住宿费收据。"
                        + "本科生宿舍为4人间，研究生为2人间。入住后填写物品交接单。");
        ingest(store, doc, "环节四 体检",
                "入学体检在校医院进行，需携带身份证与1寸照片2张。体检项目包括身高体重、视力、胸片、血常规。"
                        + "体检时间 8:00-11:00，需空腹。");
        ingest(store, doc, "环节五 材料提交",
                "将录取通知书、身份证复印件、户口迁移证（自愿）、档案等材料交至学院报到点。"
                        + "研究生需额外提交学位证、毕业证复印件。留学生需提交签证与体检表。");
    }

    /** 加载校园 POI 信息（FR-01-08）。 */
    public static void loadPoi(InMemoryKnowledgeStore store) {
        KnowledgeDocument doc = new KnowledgeDocument(
                DOC_POI, "校园POI信息库", KnowledgeCategory.POI,
                "后勤管理处", "v1", Instant.parse("2026-08-01T00:00:00Z"));
        ingest(store, doc, "第一食堂",
                "第一食堂位于北区，距报到处约320米。供应早中晚餐，营业时间 6:30-9:00、11:00-13:00、17:00-19:00。"
                        + "支持一卡通与微信支付。");
        ingest(store, doc, "第二食堂",
                "第二食堂位于南区，距报到处约580米。特色窗口较多，营业时间 11:00-13:00、17:00-19:00。");
        ingest(store, doc, "清真食堂",
                "清真食堂位于东区，距报到处约450米。提供清真餐饮，营业时间 6:30-19:00。");
        ingest(store, doc, "中心图书馆",
                "中心图书馆位于校园中区，开放时间 8:00-22:00。新生凭一卡通入馆，借阅上限 10 本/30 天。"
                        + "设有自习区与电子阅览室。");
        ingest(store, doc, "校医院",
                "校医院位于校园西北角，24小时急诊。门诊时间 8:00-17:00。入学体检在校医院一楼进行。");
    }

    /** 加载常见问题 FAQ（FR-01-09）。 */
    public static void loadFaq(InMemoryKnowledgeStore store) {
        KnowledgeDocument doc = new KnowledgeDocument(
                DOC_FAQ, "迎新常见问题FAQ", KnowledgeCategory.FAQ,
                "学生处", "v1", Instant.parse("2026-08-01T00:00:00Z"));
        ingest(store, doc, "报到需要带什么材料",
                "本科新生报到需携带：录取通知书原件及复印件1份、身份证原件及复印件2份、"
                        + "一寸蓝底免冠照片8张、高中档案（密封）。研究生另需学位证毕业证。");
        ingest(store, doc, "宿舍有没有空调",
                "本科生宿舍与研究生宿舍均配备空调，电费自理，需在宿管处充值。宿舍晚 11 点熄灯（空调不熄）。");
        ingest(store, doc, "学费怎么交",
                "学费可在财务处一楼大厅缴纳，支持微信、支付宝、银行卡。也可通过学校微信公众号提前在线缴费。"
                        + "建议提前在线缴费以减少排队。");
        ingest(store, doc, "校园网怎么连",
                "校园WiFi名称为 CampusEdu，账号为学号，初始密码为身份证后6位。首次登录需修改密码。"
                        + "如无法连接可到信息中心（图书馆一楼）咨询。");
        ingest(store, doc, "录取通知书一定要带吗",
                "录取通知书是必备材料，用于身份核验与报到注册。若遗失需提前联系招生办补办证明，否则无法完成报到。");
    }

    /** 加载材料清单模板（FR-01-10），按本科/研究生/留学生三身份差异化。 */
    public static void loadMaterial(InMemoryKnowledgeStore store) {
        KnowledgeDocument doc = new KnowledgeDocument(
                DOC_MATERIAL, "2026级新生材料清单模板", KnowledgeCategory.MATERIAL,
                "各学院", "v1", Instant.parse("2026-08-01T00:00:00Z"));
        ingest(store, doc, "本科新生材料清单",
                "录取通知书 原件+复印件1份 必备；身份证 原件+复印件2份 正反面复印；"
                        + "一寸免冠照片 8张 蓝底；高中档案 密封原件 不可拆封；户口迁移证 原件 自愿迁移",
                Map.of("student_category", StudentCategory.UNDERGRADUATE.name()));
        ingest(store, doc, "研究生材料清单",
                "录取通知书 原件+复印件1份 必备；身份证 原件+复印件2份 正反面复印；"
                        + "一寸免冠照片 8张 蓝底；本科毕业证 原件+复印件 必备；"
                        + "本科学位证 原件+复印件 必备；政审材料 密封原件",
                Map.of("student_category", StudentCategory.GRADUATE.name()));
        ingest(store, doc, "留学生材料清单",
                "录取通知书 原件 必备；护照 原件+复印件 必备；签证 原件+复印件 有效期内；"
                        + "体检表 原件 入境体检；最高学历证明 公证件 必备；2寸照片 10张 白底",
                Map.of("student_category", StudentCategory.INTERNATIONAL.name()));
    }

    private static void ingest(InMemoryKnowledgeStore store, KnowledgeDocument doc,
                               String section, String content) {
        store.ingest(doc, section, content, Map.of());
    }

    private static void ingest(InMemoryKnowledgeStore store, KnowledgeDocument doc,
                               String section, String content, Map<String, String> metadata) {
        store.ingest(doc, section, content, metadata);
    }
}
