// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.sql.plan;

import com.google.common.collect.Lists;
import com.starrocks.common.FeConstants;
import com.starrocks.common.Pair;
import com.starrocks.persist.gson.GsonUtils;
import com.starrocks.qe.ConnectContext;
import com.starrocks.qe.SessionVariable;
import com.starrocks.qe.VariableMgr;
import com.starrocks.sql.optimizer.OptimizerContext;
import com.starrocks.sql.optimizer.dump.QueryDumpInfo;
import com.starrocks.sql.optimizer.rule.RuleSet;
import com.starrocks.sql.optimizer.rule.transformation.JoinAssociativityRule;
import com.starrocks.system.BackendCoreStat;
import com.starrocks.thrift.TExplainLevel;
import com.starrocks.utframe.StarRocksAssert;
import com.starrocks.utframe.UtFrameUtils;
import mockit.Mock;
import mockit.MockUp;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReplayFromDumpTest {
    public static ConnectContext connectContext;
    public static StarRocksAssert starRocksAssert;

    public static List<String> MODEL_LISTS = Lists.newArrayList("[end]", "[dump]", "[result]", "[fragment]",
            "[fragment statistics]");

    @BeforeClass
    public static void beforeClass() throws Exception {
        UtFrameUtils.createMinStarRocksCluster();
        // create connect context
        connectContext = UtFrameUtils.createDefaultCtx();
        connectContext.getSessionVariable().setOptimizerExecuteTimeout(30000);
        connectContext.getSessionVariable().setJoinImplementationMode("auto");
        starRocksAssert = new StarRocksAssert(connectContext);
        FeConstants.runningUnitTest = true;
    }

    @Before
    public void before() {
        BackendCoreStat.reset();
    }

    public String getModelContent(String filename, String model) {
        String path = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource("sql")).getPath();
        File file = new File(path + "/" + filename + ".sql");
        StringBuilder modelContentBuilder = new StringBuilder();

        String tempStr;
        boolean hasModelContent = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while ((tempStr = reader.readLine()) != null) {
                if (tempStr.equals("[" + model + "]")) {
                    hasModelContent = true;
                    continue;
                }

                if (MODEL_LISTS.contains(tempStr)) {
                    hasModelContent = false;
                }

                if (hasModelContent) {
                    modelContentBuilder.append(tempStr).append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
        return modelContentBuilder.toString();
    }

    public QueryDumpInfo getDumpInfoFromJson(String dumpInfoString) {
        return GsonUtils.GSON.fromJson(dumpInfoString, QueryDumpInfo.class);
    }

    public SessionVariable getTestSessionVariable() {
        SessionVariable sessionVariable = VariableMgr.newSessionVariable();
        sessionVariable.setMaxTransformReorderJoins(8);
        sessionVariable.setEnableGlobalRuntimeFilter(true);
        sessionVariable.setEnableMultiColumnsOnGlobbalRuntimeFilter(true);
        return sessionVariable;
    }

    public void compareDumpWithOriginTest(String filename) throws Exception {
        String dumpString = getModelContent(filename, "dump");
        String originCostPlan = Stream.of(getModelContent(filename, "fragment statistics").split("\n")).
                filter(s -> !s.contains("tabletList")).collect(Collectors.joining("\n"));
        String replayCostPlan = Stream.of(
                        PlanTestBase.format(getCostPlanFragment(dumpString, getTestSessionVariable()).second).split("\n"))
                .filter(s -> !s.contains("tabletList")).collect(Collectors.joining("\n"));
        Assert.assertEquals(originCostPlan, replayCostPlan);
    }

    private String getDumpInfoFromFile(String fileName) throws Exception {
        String path = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource("sql")).getPath();
        File file = new File(path + "/" + fileName + ".json");
        StringBuilder sb = new StringBuilder();
        String tempStr;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while ((tempStr = reader.readLine()) != null) {
                sb.append(tempStr);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail();
        }

        return sb.toString();
    }

    private Pair<QueryDumpInfo, String> getCostPlanFragment(String dumpJonStr) throws Exception {
        return getCostPlanFragment(dumpJonStr, null);
    }

    private Pair<QueryDumpInfo, String> getCostPlanFragment(String dumpJsonStr, SessionVariable sessionVariable)
            throws Exception {
        return getPlanFragment(dumpJsonStr, sessionVariable, TExplainLevel.COSTS);
    }

    private Pair<QueryDumpInfo, String> getPlanFragment(String dumpJsonStr, SessionVariable sessionVariable,
                                                        TExplainLevel level)
            throws Exception {
        QueryDumpInfo queryDumpInfo = getDumpInfoFromJson(dumpJsonStr);
        if (sessionVariable != null) {
            queryDumpInfo.setSessionVariable(sessionVariable);
        }
        queryDumpInfo.getSessionVariable().setOptimizerExecuteTimeout(30000);
        return new Pair<>(queryDumpInfo,
                UtFrameUtils.getNewPlanAndFragmentFromDump(connectContext, queryDumpInfo).second.
                        getExplainString(level));
    }

    @Test
    public void testTPCH1() throws Exception {
        compareDumpWithOriginTest("tpchcost/q1");
    }

    @Test
    public void testTPCH5() throws Exception {
        compareDumpWithOriginTest("tpchcost/q5");
    }

    @Test
    public void testTPCH7() throws Exception {
        compareDumpWithOriginTest("tpchcost/q7");
    }

    @Test
    public void testTPCH17WithUse2AggStage() throws Exception {
        QueryDumpInfo queryDumpInfo = getDumpInfoFromJson(getDumpInfoFromFile("query_dump/tpch17"));
        SessionVariable sessionVariable = queryDumpInfo.getSessionVariable();
        sessionVariable.setNewPlanerAggStage(2);
        Pair<QueryDumpInfo, String> replayPair =
                getCostPlanFragment(getDumpInfoFromFile("query_dump/tpch17"), sessionVariable);
        Assert.assertTrue(replayPair.second.contains("1:AGGREGATE (update serialize)"));
        Assert.assertTrue(replayPair.second.contains("3:AGGREGATE (merge finalize)"));
    }

    @Test
    public void testTPCH20() throws Exception {
        compareDumpWithOriginTest("tpchcost/q20");
    }

    @Test
    public void testReplayTPCDS02() throws Exception {
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/tpcds02"));
        SessionVariable replaySessionVariable = replayPair.first.getSessionVariable();
        Assert.assertEquals(replaySessionVariable.getParallelExecInstanceNum(), 4);
        Assert.assertTrue(replayPair.second.contains("  |----24:EXCHANGE\n" +
                "  |       cardinality: 65744\n" +
                "  |    \n" +
                "  18:UNION\n" +
                "  |  child exprs:\n" +
                "  |      [325, INT, true] | [346, DECIMAL64(7,2), true]\n" +
                "  |      [359, INT, true] | [380, DECIMAL64(7,2), true]"));
    }

    @Test
    public void testSSB10() throws Exception {
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/ssb10"));
        Assert.assertTrue(replayPair.second, replayPair.second.contains("  14:Project\n" +
                "  |  output columns:\n" +
                "  |  13 <-> [13: lo_revenue, INT, false]\n" +
                "  |  22 <-> [22: d_year, INT, false]\n" +
                "  |  38 <-> [38: c_city, VARCHAR, false]\n" +
                "  |  46 <-> [46: s_city, VARCHAR, false]\n" +
                "  |  cardinality: 28532"));
        Assert.assertTrue(replayPair.second.contains("  |----7:EXCHANGE\n"
                + "  |       cardinality: 30"));
    }

    @Test
    public void testTPCDS54() throws Exception {
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/tpcds54"));
        // Check the size of the left and right tables
        Assert.assertTrue(replayPair.second, replayPair.second.contains("  49:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  other join predicates: cast([934: d_month_seq, INT, true] as BIGINT) <= [1017: expr, BIGINT, true]\n" +
                "  |  cardinality: 18262\n" +
                "  |  column statistics: \n" +
                "  |  * d_date_sk-->[2415022.0, 2488070.0, 0.0, 4.0, 18262.25] ESTIMATE\n" +
                "  |  * d_month_seq-->[0.0, 2400.0, 0.0, 4.0, 2398.0] ESTIMATE\n" +
                "  |  * expr-->[3.0, 2403.0, 0.0, 8.0, 30.13572607260726] ESTIMATE\n" +
                "  |  \n" +
                "  |----48:EXCHANGE\n" +
                "  |       cardinality: 1\n" +
                "  |    \n" +
                "  41:Project\n" +
                "  |  output columns:\n" +
                "  |  931 <-> [931: d_date_sk, INT, false]\n" +
                "  |  934 <-> [934: d_month_seq, INT, true]\n" +
                "  |  cardinality: 36525\n" +
                "  |  column statistics: \n" +
                "  |  * d_date_sk-->[2415022.0, 2488070.0, 0.0, 4.0, 36524.5] ESTIMATE\n" +
                "  |  * d_month_seq-->[0.0, 2400.0, 0.0, 4.0, 2398.0] ESTIMATE\n" +
                "  |  \n" +
                "  40:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  other join predicates: cast([934: d_month_seq, INT, true] as BIGINT) >= [987: expr, BIGINT, true]\n" +
                "  |  cardinality: 36525\n" +
                "  |  column statistics: \n" +
                "  |  * d_date_sk-->[2415022.0, 2488070.0, 0.0, 4.0, 36524.5] ESTIMATE\n" +
                "  |  * d_month_seq-->[0.0, 2400.0, 0.0, 4.0, 2398.0] ESTIMATE\n" +
                "  |  * expr-->[1.0, 2401.0, 0.0, 8.0, 30.13572607260726] ESTIMATE\n" +
                "  |  \n" +
                "  |----39:EXCHANGE\n" +
                "  |       cardinality: 1\n" +
                "  |    \n" +
                "  32:OlapScanNode\n" +
                "     table: date_dim, rollup: date_dim"));
        Assert.assertTrue(replayPair.second.contains(" |----18:EXCHANGE\n" +
                "  |       cardinality: 6304\n" +
                "  |    \n" +
                "  2:OlapScanNode\n" +
                "     table: customer, rollup: customer"));
    }

    @Test
    public void testTPCDS23_1() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/tpcds23_1"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second, replayPair.second.contains("  MultiCastDataSinks\n" +
                "  STREAM DATA SINK\n" +
                "    EXCHANGE ID: 52\n" +
                "    RANDOM\n" +
                "  STREAM DATA SINK\n" +
                "    EXCHANGE ID: 73\n" +
                "    RANDOM\n" +
                "\n" +
                "  40:Project\n" +
                "  |  <slot 171> : 171: c_customer_sk\n" +
                "  |  \n" +
                "  39:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: CAST(190: sum AS DOUBLE) > CAST(0.5 * 262: max AS DOUBLE)"));
    }

    @Test
    public void testTPCH01() throws Exception {
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/tpch01"));
        // check q1 has two agg phase with default column statistics
        Assert.assertTrue(replayPair.second.contains("AGGREGATE (merge finalize)"));
        Assert.assertTrue(replayPair.second.contains("AGGREGATE (update serialize)"));
    }

    @Test
    public void testGroupByLimit() throws Exception {
        // check can generate 1 phase with limit 1
        // This test has column statistics and accurate table row count
        SessionVariable sessionVariable = VariableMgr.newSessionVariable();
        sessionVariable.setNewPlanerAggStage(1);
        Pair<QueryDumpInfo, String> replayPair =
                getCostPlanFragment(getDumpInfoFromFile("query_dump/groupby_limit"), sessionVariable);
        Assert.assertTrue(replayPair.second.contains("1:AGGREGATE (update finalize)"));
    }

    @Test
    public void testTPCDS77() throws Exception {
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/tpcds77"));
        // check can generate plan without exception
        System.out.println(replayPair.second);
    }

    @Test
    public void testTPCDS78() throws Exception {
        // check outer join with isNull predicate on inner table
        // The estimate cardinality of join should not be 0.
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/tpcds78"));
        System.out.println(replayPair.second);
        Assert.assertTrue(replayPair.second.contains("3:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BUCKET_SHUFFLE)\n" +
                "  |  equal join conjunct: [257: ss_ticket_number, INT, false] = [280: sr_ticket_number, INT, true]\n" +
                "  |  equal join conjunct: [256: ss_item_sk, INT, false] = [279: sr_item_sk, INT, true]\n" +
                "  |  other predicates: 280: sr_ticket_number IS NULL\n" +
                "  |  output columns: 256, 258, 260, 266, 267, 269\n" +
                "  |  cardinality: 37372757"));
        Assert.assertTrue(replayPair.second.contains("15:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BUCKET_SHUFFLE)\n" +
                "  |  equal join conjunct: [331: ws_order_number, INT, false] = [365: wr_order_number, INT, true]\n" +
                "  |  equal join conjunct: [330: ws_item_sk, INT, false] = [364: wr_item_sk, INT, true]\n" +
                "  |  other predicates: 365: wr_order_number IS NULL\n" +
                "  |  output columns: 330, 332, 335, 348, 349, 351\n" +
                "  |  cardinality: 7914602"));
    }

    @Test
    public void testTPCDS94() throws Exception {
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/tpcds94"));
        // check ANTI JOIN cardinality is not 0
        Assert.assertTrue(replayPair.second.contains(" 21:HASH JOIN\n" +
                "  |  join op: RIGHT ANTI JOIN (PARTITIONED)\n" +
                "  |  equal join conjunct: [138: wr_order_number, INT, false] = [2: ws_order_number, INT, false]\n" +
                "  |  build runtime filters:\n" +
                "  |  - filter_id = 3, build_expr = (2: ws_order_number), remote = true\n" +
                "  |  output columns: 2, 17, 29, 34\n" +
                "  |  cardinality: 880438"));
    }

    @Test
    public void testTPCDS22() throws Exception {
        Pair<QueryDumpInfo, String> replayPair = getCostPlanFragment(getDumpInfoFromFile("query_dump/tpcds22"));
        // check d_date_sk distinct values has adjusted according to the cardinality
        Assert.assertTrue(replayPair.second.contains("  4:HASH JOIN\n" +
                "  |  join op: INNER JOIN (BROADCAST)\n" +
                "  |  equal join conjunct: [1: inv_date_sk, INT, false] = [5: d_date_sk, INT, false]\n" +
                "  |  build runtime filters:\n" +
                "  |  - filter_id = 0, build_expr = (5: d_date_sk), remote = false\n" +
                "  |  output columns: 2, 4\n" +
                "  |  cardinality: 399330000\n" +
                "  |  column statistics: \n" +
                "  |  * inv_date_sk-->[2450815.0, 2452635.0, 0.0, 4.0, 260.0] ESTIMATE\n" +
                "  |  * inv_item_sk-->[1.0, 204000.0, 0.0, 4.0, 200414.0] ESTIMATE\n" +
                "  |  * inv_quantity_on_hand-->[0.0, 1000.0, 0.05000724964315228, 4.0, 1006.0] ESTIMATE\n" +
                "  |  * d_date_sk-->[2450815.0, 2452635.0, 0.0, 4.0, 260.0] ESTIMATE\n" +
                "  |  \n" +
                "  |----3:EXCHANGE\n" +
                "  |       cardinality: 335"));
    }

    @Test
    public void testTPCDS54WithJoinHint() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/tpcds54_with_join_hint"), null, TExplainLevel.NORMAL);
        // checkout join order as hint
        Assert.assertTrue(replayPair.second.contains(" 19:HASH JOIN\n" +
                "  |  join op: INNER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 729: ss_sold_date_sk = 750: d_date_sk\n" +
                "  |  \n" +
                "  |----18:EXCHANGE\n" +
                "  |    \n" +
                "  0:OlapScanNode\n" +
                "     TABLE: store_sales"));
    }

    @Test
    public void testCrossReorder() throws Exception {
        RuleSet mockRule = new RuleSet() {
            @Override
            public void addJoinTransformationRules() {
                this.getTransformRules().clear();
                this.getTransformRules().add(JoinAssociativityRule.getInstance());
            }
        };

        new MockUp<OptimizerContext>() {
            @Mock
            public RuleSet getRuleSet() {
                return mockRule;
            }
        };

        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/cross_reorder"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second, replayPair.second.contains("  14:NESTLOOP JOIN\n" +
                "  |  join op: INNER JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: CASE WHEN CAST(6: v3 AS BOOLEAN) THEN CAST(11: v2 AS VARCHAR) " +
                "WHEN CAST(3: v3 AS BOOLEAN) THEN '123' ELSE CAST(12: v3 AS VARCHAR) END > '1', " +
                "(2: v2 = CAST(8: v2 AS VARCHAR(1048576))) OR (3: v3 = 8: v2)\n"));
    }

    @Test
    public void testJoinReorderPushColumnsNoHandleProject() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/join_reorder"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second.contains("  |  <slot 40> : CAST(15: id_smallint AS INT)\n" +
                "  |  <slot 41> : CAST(23: id_date AS DATETIME)\n" +
                "  |  \n" +
                "  5:OlapScanNode\n" +
                "     TABLE: external_es_table_without_null"));
    }

    @Test
    public void testMultiCountDistinct() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/multi_count_distinct"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second, replayPair.second.contains("  32:AGGREGATE (update serialize)\n" +
                "  |  STREAMING\n" +
                "  |  output: multi_distinct_count(6: order_id), multi_distinct_count(11: delivery_phone), " +
                "multi_distinct_count(128: case), max(103: count)"));
    }

    @Test
    public void testJoinWithPipelineDop() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/join_pipeline_dop"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second.contains("25:HASH JOIN\n" +
                "  |  join op: INNER JOIN (PARTITIONED)"));
    }

    @Test
    public void testDecodeLimitWithProject() throws Exception {
        FeConstants.USE_MOCK_DICT_MANAGER = true;
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/decode_limit_with_project"), null,
                        TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second.contains("  14:Decode\n" +
                "  |  <dict id 42> : <string id 18>"));
        FeConstants.USE_MOCK_DICT_MANAGER = false;
    }

    @Test
    public void testCountDistinctWithLimit() throws Exception {
        // check use two stage agg
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/count_distinct_limit"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second.contains("1:AGGREGATE (update serialize)\n" +
                "  |  STREAMING\n" +
                "  |  output: multi_distinct_count(5: lo_suppkey)"));
        Assert.assertTrue(replayPair.second.contains("3:AGGREGATE (merge finalize)\n" +
                "  |  output: multi_distinct_count(18: count)\n" +
                "  |  group by: 10: lo_extendedprice, 13: lo_revenue"));
    }

    @Test
    public void testEighteenTablesJoin() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/eighteen_tables_join"), null, TExplainLevel.NORMAL);
        // check optimizer finish task
        Assert.assertTrue(replayPair.second.contains("52:HASH JOIN\n" +
                "  |  join op: INNER JOIN (COLOCATE)"));
    }

    @Test
    public void testLocalAggregateWithoutTableRowCount() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/local_agg_without_table_rowcount"), null,
                        TExplainLevel.NORMAL);
        // check local aggregate
        Assert.assertTrue(replayPair.second.contains("1:AGGREGATE (update finalize)\n" +
                "  |  output: multi_distinct_count(4: lo_partkey)"));
    }

    @Test
    public void testLogicalAggWithOneTablet() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/local_agg_with_one_tablet"), null,
                        TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second.contains("1:AGGREGATE (update finalize)\n" +
                "  |  output: multi_distinct_count(4: t0d)"));
    }

    @Test
    public void testSelectSubqueryWithMultiJoin() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/select_sbuquery_with_multi_join"), null,
                        TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second, replayPair.second.contains("  20:Project\n" +
                "  |  <slot 6> : bitmap_and(24: bitmap_union, 32: bitmap_union)\n" +
                "  |  \n" +
                "  19:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  \n" +
                "  |----18:EXCHANGE\n" +
                "  |    \n" +
                "  11:Project\n" +
                "  |  <slot 24> : 21: bitmap_union"));
    }

    @Test
    public void testTPCHRandom() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/tpch_random"), null, TExplainLevel.NORMAL);
        // check optimizer could extract best plan
        Assert.assertTrue(replayPair.second.contains("11:HASH JOIN\n" +
                "  |  join op: INNER JOIN (BUCKET_SHUFFLE)"));
    }

    @Test
    public void testInsertWithView() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/insert_view"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second.contains(" 2:Project\n" +
                "  |  <slot 2> : 2: t2_c2\n" +
                "  |  <slot 11> : CAST(CAST(1: t2_c1 AS BIGINT) + 1 AS INT)"));
        Assert.assertTrue(replayPair.second.contains("OLAP TABLE SINK"));
    }

    @Test
    public void testMergeGroupWithDeleteBestExpression() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/merge_group_delete_best_expression"), null,
                        TExplainLevel.NORMAL);
        // check without exception
        Assert.assertTrue(replayPair.second.contains("14:HASH JOIN\n" +
                "  |  join op: INNER JOIN (PARTITIONED)"));
    }

    @Test
    public void testJoinReOrderPruneColumns() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/join_reorder_prune_columns"), null,
                        TExplainLevel.NORMAL);
        // check without exception
        Assert.assertTrue(replayPair.second.contains("<slot 186> : 186: S_SUPPKEY"));
    }

    @Test
    public void testMultiViewWithDbName() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/multi_view_with_db"), null, TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second.contains(" 0:OlapScanNode\n" +
                "     TABLE: t3"));
    }

    @Test
    public void testMultiViewCrossJoin() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/multi_view_cross_join"), null, TExplainLevel.NORMAL);
        // check without exception
        Assert.assertTrue(replayPair.second.contains(" 38:Project\n" +
                "  |  <slot 1> : 1: c_0_0"));
    }

    @Test
    public void testMultiViewPruneColumns() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/multi_view_prune_columns"), null, TExplainLevel.NORMAL);
        // check without exception
        Assert.assertTrue(replayPair.second.contains("  193:Project\n" +
                "  |  <slot 1> : 1: c_1_0"));
    }

    @Test
    public void testIntersectCardinality() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/intersect_cardinality"), null, TExplainLevel.COSTS);
        Assert.assertTrue(replayPair.second.contains("cardinality: 152160"));
    }

    @Test
    public void testCorrelatedSubqueryWithEqualsExpressions() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/correlated_subquery_with_equals_expression"), null,
                        TExplainLevel.NORMAL);
        Assert.assertTrue(replayPair.second, replayPair.second.contains(" 21:NESTLOOP JOIN\n" +
                "  |  join op: INNER JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: if(20: c_0_0 != 1: c_0_0, 4: c_0_3, 21: c_0_3) = '1969-12-28', " +
                "CASE WHEN (22: countRows IS NULL) OR (22: countRows = 0) THEN FALSE " +
                "WHEN 1: c_0_0 IS NULL THEN NULL WHEN 16: c_0_0 IS NOT NULL THEN TRUE " +
                "WHEN 23: countNotNulls < 22: countRows THEN NULL ELSE FALSE END IS NULL"));
        Assert.assertTrue(replayPair.second.contains("14:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 1: c_0_0 = 16: c_0_0\n" +
                "  |  other join predicates: if(16: c_0_0 != 1: c_0_0, 4: c_0_3, 18: c_0_3) = '1969-12-28'"));
    }

    @Test
    public void testHiveTPCH02UsingResource() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/hive_tpch02_resource"), null, TExplainLevel.COSTS);
        Assert.assertTrue(replayPair.second.contains("21:HASH JOIN\n" +
                "  |  join op: INNER JOIN (BUCKET_SHUFFLE(S))\n" +
                "  |  equal join conjunct: [29: ps_partkey, INT, true] = [1: p_partkey, INT, true]\n" +
                "  |  build runtime filters:\n" +
                "  |  - filter_id = 3, build_expr = (1: p_partkey), remote = true\n" +
                "  |  output columns: 1, 3, 48\n" +
                "  |  cardinality: 138889\n" +
                "  |  column statistics: \n" +
                "  |  * p_partkey-->[1.0, 2.0E7, 0.0, 4.0, 138888.88888888888] ESTIMATE\n" +
                "  |  * p_mfgr-->[-Infinity, Infinity, 0.0, 14.0, 5.0] ESTIMATE\n" +
                "  |  * ps_partkey-->[1.0, 2.0E7, 0.0, 4.0, 138888.88888888888] ESTIMATE\n" +
                "  |  * min-->[-Infinity, Infinity, 0.0, 8.0, 100756.0] ESTIMATE\n" +
                "  |  \n" +
                "  |----20:EXCHANGE\n" +
                "  |       cardinality: 138889\n" +
                "  |    \n" +
                "  17:AGGREGATE (update finalize)"));
    }

    @Test
    public void testHiveTPCH08UsingResource() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/hive_tpch08_resource"), null, TExplainLevel.COSTS);
        Assert.assertTrue(replayPair.second.contains("33:HASH JOIN\n" +
                "  |  join op: INNER JOIN (PARTITIONED)\n" +
                "  |  equal join conjunct: [33: o_orderkey, INT, true] = [17: l_orderkey, INT, true]\n" +
                "  |  build runtime filters:\n" +
                "  |  - filter_id = 6, build_expr = (17: l_orderkey), remote = true\n" +
                "  |  output columns: 22, 23, 37, 55\n" +
                "  |  cardinality: 2403381"));
    }

    @Test
    public void testHiveTwoTableJoin() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/hive_table_join"), null, TExplainLevel.COSTS);
        Assert.assertTrue(replayPair.second.contains("0:HdfsScanNode\n" +
                "     TABLE: parttbl\n" +
                "     PARTITION PREDICATES: 6: date <= '2022-08-16'\n" +
                "     partitions=2/3\n" +
                "     avgRowSize=8.0\n" +
                "     numNodes=0\n" +
                "     cardinality: 1\n" +
                "     column statistics: \n" +
                "     * c1-->[-Infinity, Infinity, 0.0, 3.0, 1.0] ESTIMATE\n" +
                "     * date-->[-Infinity, Infinity, 0.0, 5.0, 1.0] ESTIMATE"));
        Assert.assertTrue(replayPair.second.contains("2:HdfsScanNode\n" +
                "     TABLE: parttbl\n" +
                "     PARTITION PREDICATES: 3: date = '2022-08-15'\n" +
                "     partitions=1/3\n" +
                "     avgRowSize=9.0\n" +
                "     numNodes=0\n" +
                "     cardinality: 0\n" +
                "     column statistics: \n" +
                "     * c0-->[1.0, 2.0, 0.0, 4.0, 1.0] ESTIMATE\n" +
                "     * date-->[-Infinity, Infinity, 0.0, 5.0, 1.0] ESTIMAT"));
    }

    @Test
    public void testParHiveTPCH02UsingCatalog() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/hive_tpch02_catalog"), null, TExplainLevel.COSTS);
        Assert.assertTrue(replayPair.second.contains(" 24:HASH JOIN\n" +
                "  |  join op: INNER JOIN (BROADCAST)\n" +
                "  |  equal join conjunct: [17: ps_partkey, INT, true] = [1: p_partkey, INT, true]\n" +
                "  |  equal join conjunct: [20: ps_supplycost, DECIMAL64(15,2), true] = [48: min, DECIMAL64(15,2), true]"));

    }

    @Test
    public void testParHiveTPCH08UsingCatalog() throws Exception {
        Pair<QueryDumpInfo, String> replayPair =
                getPlanFragment(getDumpInfoFromFile("query_dump/hive_tpch08_catalog"), null, TExplainLevel.COSTS);
        Assert.assertTrue(replayPair.second.contains("21:HASH JOIN\n" +
                "  |  join op: INNER JOIN (BROADCAST)\n" +
                "  |  equal join conjunct: [18: l_partkey, INT, true] = [1: p_partkey, INT, true]\n" +
                "  |  build runtime filters:\n" +
                "  |  - filter_id = 3, build_expr = (1: p_partkey), remote = false\n" +
                "  |  output columns: 17, 19, 22, 23\n" +
                "  |  cardinality: 2174338"));
    }
}