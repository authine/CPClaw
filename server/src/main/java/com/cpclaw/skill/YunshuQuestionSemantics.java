package com.cpclaw.skill;

import com.cpclaw.cloudpivot.CloudPivotRuntimeProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** CloudPivot/Yunshu language and analysis semantics. */
@Component
public class YunshuQuestionSemantics implements SkillQuestionSemantics, SkillSemanticProvider {
    @Override public String skillId() { return SkillCatalog.YUNSHU_BUSINESS_SYSTEM; }
    @Override public SkillQuestionSemantics semantics() { return this; }
    @Override public boolean isCountQuestion(String content) {
        if (isSingleRecordDetailQuestion(content)) return false;
        return contains(content, "统计", "数量", "总计", "多少", "几条", "几个", "几项", "几笔", "几份", "几单", "一共", "总共", "共有");
    }
    @Override public boolean isAnalysisQuestion(String content) {
        if (isSingleRecordDetailQuestion(content) || isDetailCollectionQuestion(content)) return false;
        return contains(content, "分析", "洞察", "诊断", "趋势", "建议", "怎么看", "怎么样", "情况", "概况", "整体", "按年", "每年", "年度")
            || isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content)
            || isOwnerOpportunityRankingQuestion(content) || isStatusAmountAggregationQuestion(content) || isAmountRankingQuestion(content) || isAmountAggregationQuestion(content);
    }
    @Override public boolean isDetailCollectionQuestion(String content) {
        String value = compact(content);
        if (!contains(value, "列表", "清单", "明细", "详情", "记录", "数据") || isSingleRecordDetailQuestion(value)) return false;
        return !(isStageDistributionQuestion(value) || isYearlyDistributionQuestion(value) || isProvinceDistributionQuestion(value)
            || isNewOldCustomerQuestion(value) || isOwnerOpportunityRankingQuestion(value) || isStatusAmountAggregationQuestion(value)
            || isAmountRankingQuestion(value) || isAmountAggregationQuestion(value) || contains(value, "趋势", "洞察", "诊断", "建议", "占比", "比例", "分布", "排行", "排名"));
    }
    @Override public boolean isSingleRecordDetailQuestion(String content) {
        String value = compact(content);
        return contains(value, "第一条", "第1条", "第一个", "第1个", "首条", "第一笔", "第1笔", "第一单", "第1单")
            && contains(value, "明细", "详情", "详细", "信息", "内容", "返回", "查看", "看一下");
    }
    @Override public boolean isNewOldCustomerQuestion(String content) {
        String value = compact(content);
        boolean subject = contains(value, "新客户", "老客户", "新老客户", "新老", "新增客户", "存量客户") || (value.contains("客户") && value.contains("新") && value.contains("老"));
        return subject && contains(value, "多", "哪个", "哪类", "谁", "更多", "占比", "比例", "对比", "比较", "分布", "统计", "数量", "情况", "还是");
    }
    @Override public boolean isOwnerOpportunityRankingQuestion(String content) {
        String value = compact(content);
        return contains(value, "商机", "机会", "销售机会") && contains(value, "谁", "谁的", "哪个销售", "哪位销售", "哪个负责人", "负责人", "销售")
            && contains(value, "最多", "最高", "最大", "更多", "汇总", "统计", "排行", "排名");
    }
    @Override public boolean isStatusAmountAggregationQuestion(String content) {
        String value = compact(content);
        return !requestedStatusFilters(value).isEmpty() && contains(value, "多少", "几个", "几条", "数量", "金额", "项目金额", "合同额", "收入", "汇总", "合计", "总额");
    }
    @Override public boolean isAmountAggregationQuestion(String content) {
        String value = compact(content);
        return contains(value, "金额", "合同额", "收入", "总额", "多少钱", "多少金额") && contains(value, "多少", "多少钱", "多少金额", "合计", "汇总", "统计", "一共", "总共", "共有", "总额");
    }
    @Override public boolean isAmountRankingQuestion(String content) {
        String value = compact(content);
        return contains(value, "金额", "合同额", "收入", "总额", "预计金额", "商机金额", "项目金额", "多少钱") && asksRanking(value);
    }
    @Override public boolean isStageDistributionQuestion(String content) {
        String value = compact(content);
        return (contains(value, "阶段", "状态") && contains(value, "分别", "分布", "各", "哪些", "多少", "数量", "处于", "什么阶段", "什么状态"));
    }
    @Override public boolean isYearlyDistributionQuestion(String content) {
        String value = compact(content);
        return contains(value, "每年", "按年", "年度", "年份") || (value.contains("年") && contains(value, "数量", "量", "情况", "趋势"));
    }
    @Override public boolean isProvinceDistributionQuestion(String content) {
        String value = compact(content);
        return contains(value, "省份", "所属省", "哪些省", "哪个省", "省市", "地区", "区域", "城市", "地域", "归属地", "所在地")
            && contains(value, "分别", "分布", "各", "哪些", "多少", "数量", "属于", "都", "按", "情况", "有");
    }
    @Override public boolean isBroadBusinessAnalysisQuestion(String content) {
        return isAnalysisQuestion(content) && !isStageDistributionQuestion(content) && !isYearlyDistributionQuestion(content) && !isProvinceDistributionQuestion(content)
            && !isNewOldCustomerQuestion(content) && !isOwnerOpportunityRankingQuestion(content) && !isStatusAmountAggregationQuestion(content)
            && !isAmountRankingQuestion(content) && !isAmountAggregationQuestion(content);
    }
    @Override public boolean isPlainListQuestion(String content) {
        return !isCountQuestion(content) && !isAnalysisQuestion(content) && !isSingleRecordDetailQuestion(content) && !isDetailCollectionQuestion(content);
    }
    @Override public boolean requiresCompleteAggregation(String content) {
        return isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content) || isOwnerOpportunityRankingQuestion(content)
            || isStatusAmountAggregationQuestion(content) || isAmountRankingQuestion(content) || isAmountAggregationQuestion(content) || isYearlyDistributionQuestion(content) || isAnalysisQuestion(content);
    }
    @Override public boolean requiresFullDimensionDetails(String content) {
        return isSingleRecordDetailQuestion(content) || isDetailCollectionQuestion(content) || isOwnerOpportunityRankingQuestion(content)
            || isStatusAmountAggregationQuestion(content) || isBroadBusinessAnalysisQuestion(content) || isPlainListQuestion(content);
    }
    @Override public List<String> requestedStatusFilters(String content) {
        String value = compact(content); List<String> statuses = new ArrayList<>();
        boolean unfinished = value.contains("未完成"), completed = contains(value, "已完成", "完成了", "完成的", "已结项", "结项");
        if (contains(value, "进行中", "在建", "执行中", "实施中") || unfinished) statuses.add("进行中");
        if (!unfinished && completed) statuses.add("已完成");
        if (contains(value, "暂停", "搁置")) statuses.add("暂停");
        if (contains(value, "终止", "关闭", "取消")) statuses.add("终止");
        return statuses.stream().distinct().toList();
    }
    @Override public boolean statusMatches(String actualStatus, List<String> targets) {
        if (targets == null || targets.isEmpty()) return true; String actual = compact(actualStatus);
        for (String target : targets) {
            if ("进行中".equals(target) && contains(actual, "进行中", "在建", "执行中", "实施中", "未完成")) return true;
            if ("已完成".equals(target) && !actual.contains("未完成") && contains(actual, "已完成", "完成", "已结项", "结项")) return true;
            if ("暂停".equals(target) && contains(actual, "暂停", "搁置")) return true;
            if ("终止".equals(target) && contains(actual, "终止", "关闭", "取消")) return true;
            if (actual.equals(target) || (!"已完成".equals(target) && actual.contains(target))) return true;
        }
        return false;
    }
    @Override public QuestionPlan plan(String content) {
        String value = compact(content); boolean metric = contains(value, "金额", "合同额", "收入", "总额", "预计金额", "商机金额", "项目金额", "多少钱");
        String operation = metric && asksRanking(value) ? "RANKING" : metric && asksAggregation(value) ? "AGGREGATION" : "DEFAULT";
        return new QuestionPlan(operation, metric ? "AMOUNT" : "NONE", requestedTopLimit(value));
    }
    @Override public int requestedRecordOrdinal(String content) {
        String value = compact(content); Matcher m = Pattern.compile("第([0-9]+)(条|个|笔|单)").matcher(value);
        if (m.find()) return Math.max(1, Integer.parseInt(m.group(1))); m = Pattern.compile("第([一二三四五六七八九十两]+)(条|个|笔|单)").matcher(value);
        if (m.find()) return Math.max(1, chineseOrdinal(m.group(1))); return contains(value, "首条", "第一条", "第一个", "第一笔", "第一单") ? 1 : 1;
    }
    @Override public int queryPageSize(String content, CloudPivotRuntimeProperties.Query q, boolean owner) {
        if (owner) return q.getOwnerFilterPageSize(); if (isSingleRecordDetailQuestion(content)) return requestedRecordOrdinal(content); if (isDetailCollectionQuestion(content)) return q.getListPageSize();
        if (isYearlyDistributionQuestion(content)) return q.getYearlyPageSize(); if (isBroadBusinessAnalysisQuestion(content)) return q.getBroadAnalysisPageSize();
        return (isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content) || isOwnerOpportunityRankingQuestion(content) || isStatusAmountAggregationQuestion(content) || isAmountRankingQuestion(content) || isAmountAggregationQuestion(content) || isAnalysisQuestion(content)) ? q.getAnalysisPageSize() : (isCountQuestion(content) ? q.getCountPageSize() : q.getListPageSize());
    }
    @Override public int queryRecordLimit(String content, CloudPivotRuntimeProperties.Query q, boolean owner) {
        if (owner) return requiresCompleteAggregation(content) ? q.getCompleteAggregationRecordLimit() : q.getOwnerFilterRecordLimit(); if (isSingleRecordDetailQuestion(content)) return requestedRecordOrdinal(content); if (isDetailCollectionQuestion(content)) return q.getListRecordLimit();
        if (isStatusAmountAggregationQuestion(content) || isAmountAggregationQuestion(content)) return q.getCompleteAggregationRecordLimit(); if (isAmountRankingQuestion(content)) return q.getRankingRecordLimit(); if (isCountQuestion(content)) return q.getCountRecordLimit();
        if (isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content)) return q.getDimensionRecordLimit(); if (isOwnerOpportunityRankingQuestion(content)) return q.getOwnerRankingRecordLimit(); if (isYearlyDistributionQuestion(content)) return q.getYearlyRecordLimit(); if (isBroadBusinessAnalysisQuestion(content)) return q.getBroadAnalysisRecordLimit(); if (isAnalysisQuestion(content)) return q.getAnalysisRecordLimit(); return q.getListRecordLimit();
    }
    @Override public String detectIntent(String content) {
        String value = compact(content);
        if (contains(value, "同意", "审批通过", "通过审批", "驳回", "退回", "转交", "转办", "加签", "协办", "撤回流程", "终止流程")) return "workflow_action";
        if (contains(value, "待办", "未办", "未完成工作项", "已办", "已处理", "我发起的流程", "我发起", "流程实例", "流程节点", "审批记录", "工作项")) return "query_workflow";
        if (contains(value, "填写", "填报", "填一下", "补全", "根据附件", "从附件", "识别发票")) return "fill_form_from_attachment";
        if (isStatusAmountAggregationQuestion(value) || isAmountRankingQuestion(value)) return "analyze_data";
        if (contains(value, "删除", "删掉", "移除", "作废") || (value.contains("取消") && contains(value, "取消这个", "取消这条", "取消第"))) return "delete_data";
        if (contains(value, "新增", "新建", "创建", "录入", "登记")) return "create_data";
        if (contains(value, "写入", "修改", "更新", "调整", "变更", "编辑", "保存", "提交", "发起", "分配", "转移", "关闭", "推进", "写一条跟进")) return "update_data";
        if (isAnalysisQuestion(value)) return "analyze_data";
        if (contains(value, "查询", "查", "查看", "找", "搜索", "检索", "看看", "看一下", "帮我看", "列出", "展示", "打开", "给我看", "有哪些", "有没有", "多少", "几条", "几个", "几项", "几笔", "几份", "几单", "一共", "总共", "共有", "总计", "数量", "统计", "汇总", "明细", "列表", "清单", "情况", "数据", "了解")) return "query_data";
        return "unknown";
    }
    @Override public boolean looksLikeAnalysis(String content) {
        String value = compact(content);
        return contains(value, "金额", "收入", "总额", "合计", "汇总", "平均", "最大", "最高", "最低", "最多", "排名", "排行", "占比", "比例", "趋势", "分布", "阶段", "状态", "负责人", "销售", "省份", "地区", "每年", "按年")
            && contains(value, "多少", "几个", "几条", "情况", "怎么样", "怎么看", "分析", "统计", "比较", "对比", "谁", "哪个");
    }
    @Override public String analysisDimension(String content) {
        String value = compact(content);
        if (contains(value, "每年", "按年", "年度", "年份")) return "年份";
        if (contains(value, "阶段", "状态")) return "阶段/状态";
        if (contains(value, "省份", "所属省", "哪些省", "哪个省", "地区", "区域", "城市", "地域", "归属地", "所在地", "省市")) return "省份/区域";
        if (contains(value, "负责人", "销售", "人员")) return "负责人";
        if (contains(value, "金额", "收入")) return "金额";
        return "无明确维度";
    }
    @Override public String filterSummary(String content) {
        String owner = ownerFilter(content); if (!owner.isBlank()) return "负责人/销售=" + owner;
        String value = compact(content); if (contains(value, "本月", "这个月")) return "本月"; if (contains(value, "今年", "本年")) return "今年"; if (value.contains("去年")) return "去年"; return "无明确筛选条件";
    }
    @Override public String ownerFilter(String content) {
        String value = compact(content); if (value.isBlank()) return "";
        for (String suffix : List.of("名下有多少", "名下有几个", "名下有几条", "负责的", "负责了", "负责多少", "销售的")) { int i = value.indexOf(suffix); if (i > 0) return cleanOwner(value.substring(0, i)); }
        Matcher m = Pattern.compile("(?:负责人|销售|业务员|归属销售|owner)(?:是|为|=|：|:)?([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9·._-]{1,15})").matcher(value); return m.find() ? cleanOwner(m.group(1)) : "";
    }
    @Override public List<String> expandSearchTerms(List<String> terms) {
        List<String> expanded = new ArrayList<>();
        for (String term : terms) {
            if (term == null || term.isBlank()) continue;
            expanded.add(term);
            switch (term.toLowerCase(Locale.ROOT)) {
                case "销售机会", "机会", "opportunity", "oppor" -> expanded.add("商机");
                case "潜客", "lead", "clue" -> expanded.add("线索");
                default -> { }
            }
        }
        return expanded.stream().distinct().toList();
    }
    private String cleanOwner(String value) { return value.replaceAll("^(请问|帮我查|帮我看看|查询|统计|分析|系统|现在|当前)+", "").replaceAll("(名下|负责|销售|负责人|业务员|归属销售|有多少|有几个|有几条|多少|几个|几条|数据|信息|情况)+$", ""); }
    private boolean asksRanking(String v) { return contains(v, "比较高", "较高", "最高", "最大", "最多", "排名", "排行", "排序", "前几", "前十", "前10") || v.toLowerCase(Locale.ROOT).contains("top") || (contains(v, "哪些", "哪个") && contains(v, "高", "大")); }
    private boolean asksAggregation(String v) { return contains(v, "多少", "多少钱", "多少金额", "合计", "汇总", "统计", "一共", "总共", "共有", "总额"); }
    private int requestedTopLimit(String v) { Matcher m = Pattern.compile("(?:前|top|TOP)(\\d{1,2})").matcher(v); if (m.find()) try { return Math.max(1, Math.min(20, Integer.parseInt(m.group(1)))); } catch (NumberFormatException ignored) {} return v.contains("前五") ? 5 : v.contains("前三") ? 3 : 10; }
    private int chineseOrdinal(String v) { return switch (v) { case "一" -> 1; case "二", "两" -> 2; case "三" -> 3; case "四" -> 4; case "五" -> 5; case "六" -> 6; case "七" -> 7; case "八" -> 8; case "九" -> 9; case "十" -> 10; default -> 1; }; }
    private String compact(String v) { return v == null ? "" : v.replaceAll("\\s+", ""); }
    private boolean contains(String v, String... terms) { String s = v == null ? "" : v; for (String t : terms) if (s.contains(t)) return true; return false; }
}
