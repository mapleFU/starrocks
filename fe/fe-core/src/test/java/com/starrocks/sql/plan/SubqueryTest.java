// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

package com.starrocks.sql.plan;

import com.starrocks.common.FeConstants;
import com.starrocks.qe.SessionVariable;
import com.starrocks.sql.analyzer.SemanticException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SubqueryTest extends PlanTestBase {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void testCorrelatedSubqueryWithEqualsExpressions() throws Exception {
        String sql = "select t0.v1 from t0 where (t0.v2 in (select t1.v4 from t1 where t0.v3 + t1.v5 = 1)) is NULL";
        String plan = getFragmentPlan(sql);
        Assert.assertTrue(plan, plan.contains("15:NESTLOOP JOIN\n" +
                "  |  join op: INNER JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: 3: v3 + 11: v5 = 1, CASE WHEN (12: countRows IS NULL) " +
                "OR (12: countRows = 0) THEN FALSE WHEN 2: v2 IS NULL THEN NULL WHEN 8: v4 IS NOT NULL " +
                "THEN TRUE WHEN 13: countNotNulls < 12: countRows THEN NULL ELSE FALSE END IS NULL"));
        assertContains(plan, "8:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 2: v2 = 8: v4\n" +
                "  |  other join predicates: 3: v3 + 9: v5 = 1");
    }

    @Test
    public void testCountConstantWithSubquery() throws Exception {
        String sql = "SELECT 1 FROM (SELECT COUNT(1) FROM t0 WHERE false) t;";
        String thriftPlan = getThriftPlan(sql);
        Assert.assertTrue(thriftPlan.contains("function_name:count"));
    }

    @Test
    public void testSubqueryGatherJoin() throws Exception {
        String sql = "select t1.v5 from (select * from t0 limit 1) as x inner join t1 on x.v1 = t1.v4";
        String plan = getFragmentPlan(sql);
        assertContains(plan, " OUTPUT EXPRS:\n"
                + "  PARTITION: RANDOM\n"
                + "\n"
                + "  STREAM DATA SINK\n"
                + "    EXCHANGE ID: 02\n"
                + "    UNPARTITIONED\n"
                + "\n"
                + "  1:OlapScanNode\n"
                + "     TABLE: t0");
    }

    @Test
    public void testSubqueryBroadJoin() throws Exception {
        String sql = "select t1.v5 from t0 inner join[broadcast] t1 on cast(t0.v1 as int) = cast(t1.v4 as int)";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "  |  equal join conjunct: 7: cast = 8: cast\n");
        assertContains(plan, "<slot 7> : CAST(1: v1 AS INT)");
        assertContains(plan, "<slot 8> : CAST(4: v4 AS INT)");
    }

    @Test
    public void testMultiScalarSubquery() throws Exception {
        String sql = "SELECT CASE \n"
                + "    WHEN (SELECT count(*) FROM t1 WHERE v4 BETWEEN 1 AND 20) > 74219\n"
                + "    THEN ( \n"
                + "        SELECT avg(v7) FROM t2 WHERE v7 BETWEEN 1 AND 20\n"
                + "        )\n"
                + "    ELSE (\n"
                + "        SELECT avg(v8) FROM t2 WHERE v8 BETWEEN 1 AND 20\n"
                + "        ) END AS bucket1\n"
                + "FROM t0\n"
                + "WHERE v1 = 1;";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "  14:Project\n" +
                "  |  <slot 9> : 9: count\n" +
                "  |  <slot 14> : 13: avg\n" +
                "  |  \n" +
                "  13:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  \n" +
                "  |----12:EXCHANGE\n" +
                "  |    \n" +
                "  7:Project");
        assertContains(plan, "  STREAM DATA SINK\n" +
                "    EXCHANGE ID: 17\n" +
                "    UNPARTITIONED\n" +
                "\n" +
                "  16:AGGREGATE (update finalize)\n" +
                "  |  output: avg(16: v8)");
    }

    @Test
    public void testSubqueryLimit() throws Exception {
        String sql = "select * from t0 where 2 = (select v4 from t1 limit 1);";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "4:SELECT\n" +
                "  |  predicates: 4: v4 = 2\n" +
                "  |  \n" +
                "  3:ASSERT NUMBER OF ROWS\n" +
                "  |  assert number of rows: LE 1");
    }

    @Test
    public void testUnionSubqueryDefaultLimit() throws Exception {
        connectContext.getSessionVariable().setSqlSelectLimit(2);
        String sql = "select * from (select * from t0 union all select * from t0) xx limit 10;";
        String plan = getFragmentPlan(sql);
        connectContext.getSessionVariable().setSqlSelectLimit(SessionVariable.DEFAULT_SELECT_LIMIT);
        assertContains(plan, "RESULT SINK\n" +
                "\n" +
                "  5:EXCHANGE\n" +
                "     limit: 10");
        assertContains(plan, "  0:UNION\n" +
                "  |  limit: 10\n" +
                "  |  \n" +
                "  |----4:EXCHANGE\n" +
                "  |       limit: 10\n" +
                "  |    \n" +
                "  2:EXCHANGE\n" +
                "     limit: 10\n");
    }

    @Test
    public void testExistsRewrite() throws Exception {
        String sql =
                "select count(*) FROM  test.join1 WHERE  EXISTS (select max(id) from test.join2 where join2.id = join1.id)";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "LEFT SEMI JOIN");
    }

    @Test
    public void testMultiNotExistPredicatePushDown() throws Exception {
        FeConstants.runningUnitTest = true;
        connectContext.setDatabase("test");

        String sql =
                "select * from join1 where join1.dt > 1 and NOT EXISTS " +
                        "(select * from join1 as a where join1.dt = 1 and a.id = join1.id)" +
                        "and NOT EXISTS (select * from join1 as a where join1.dt = 2 and a.id = join1.id);";
        String plan = getFragmentPlan(sql);

        assertContains(plan, "  5:HASH JOIN\n" +
                "  |  join op: RIGHT ANTI JOIN (COLOCATE)\n" +
                "  |  colocate: true\n" +
                "  |  equal join conjunct: 9: id = 2: id\n" +
                "  |  other join predicates: 1: dt = 2");
        assertContains(plan, "  |    3:HASH JOIN\n" +
                "  |    |  join op: LEFT ANTI JOIN (COLOCATE)\n" +
                "  |    |  colocate: true\n" +
                "  |    |  equal join conjunct: 2: id = 5: id\n" +
                "  |    |  other join predicates: 1: dt = 1");
        assertContains(plan, "  |    1:OlapScanNode\n" +
                "  |       TABLE: join1\n" +
                "  |       PREAGGREGATION: ON\n" +
                "  |       PREDICATES: 1: dt > 1");
        FeConstants.runningUnitTest = false;
    }

    @Test
    public void testAssertWithJoin() throws Exception {
        String sql =
                "SELECT max(1) FROM t0 WHERE 1 = (SELECT t1.v4 FROM t0, t1 WHERE t1.v4 IN (SELECT t1.v4 FROM  t1))";
        String plan = getFragmentPlan(sql);
        assertContains(plan, ("9:Project\n" +
                "  |  <slot 7> : 7: v4\n" +
                "  |  \n" +
                "  8:HASH JOIN\n" +
                "  |  join op: LEFT SEMI JOIN (BROADCAST)"));
    }

    @Test
    public void testCorrelatedSubQuery() throws Exception {
        String sql =
                "select count(*) from t2 where (select v4 from t1 where (select v1 from t0 where t2.v7 = 1) = 1)  = 1";
        expectedEx.expect(SemanticException.class);
        expectedEx.expectMessage("Column '`test`.`t2`.`v7`' cannot be resolved");
        getFragmentPlan(sql);
    }

    @Test
    public void testConstScalarSubQuery() throws Exception {
        String sql = "select * from t0 where 2 = (select v4 from t1)";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "4:SELECT\n" +
                "  |  predicates: 4: v4 = 2\n" +
                "  |  \n" +
                "  3:ASSERT NUMBER OF ROWS");
        assertContains(plan, "1:OlapScanNode\n" +
                "     TABLE: t1\n" +
                "     PREAGGREGATION: ON\n" +
                "     partitions=0/1");
    }

    @Test
    public void testCorrelatedComplexInSubQuery() throws Exception {
        String sql = "SELECT v4  FROM t1\n" +
                "WHERE ( (\"1969-12-09 14:18:03\") IN (\n" +
                "          SELECT t2.v8 FROM t2 WHERE (t1.v5) = (t2.v9))\n" +
                "    ) IS NULL\n";
        Assert.assertThrows(SemanticException.class, () -> getFragmentPlan(sql));
    }

    @Test
    public void testInSubQueryWithAggAndPredicate() throws Exception {
        FeConstants.runningUnitTest = true;
        {
            String sql = "SELECT DISTINCT 1\n" +
                    "FROM test_all_type\n" +
                    "WHERE (t1a IN \n" +
                    "   (\n" +
                    "      SELECT v1\n" +
                    "      FROM t0\n" +
                    "   )\n" +
                    ")IS NULL";

            String plan = getFragmentPlan(sql);
            assertContains(plan, "  18:Project\n" +
                    "  |  <slot 15> : 1\n" +
                    "  |  \n" +
                    "  17:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN");
        }
        {
            String sql = "SELECT DISTINCT 1\n" +
                    "FROM test_all_type\n" +
                    "WHERE t1a IN \n" +
                    "   (\n" +
                    "      SELECT v1\n" +
                    "      FROM t0\n" +
                    "   )\n" +
                    "IS NULL";

            String plan = getFragmentPlan(sql);
            assertContains(plan, "  18:Project\n" +
                    "  |  <slot 15> : 1\n" +
                    "  |  \n" +
                    "  17:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN");
        }
        {
            String sql = "SELECT DISTINCT(t1d)\n" +
                    "FROM test_all_type\n" +
                    "WHERE (t1a IN \n" +
                    "   (\n" +
                    "      SELECT v1\n" +
                    "      FROM t0\n" +
                    "   )\n" +
                    ")IS NULL";

            String plan = getFragmentPlan(sql);
            assertContains(plan, "  18:Project\n" +
                    "  |  <slot 4> : 4: t1d\n" +
                    "  |  \n" +
                    "  17:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN");
        }
        FeConstants.runningUnitTest = false;
    }

    @Test
    public void testCTEAnchorProperty() throws Exception {
        String sql = "explain SELECT\n" +
                "max (t0_2.v1 IN (SELECT t0_2.v1 FROM  t0 AS t0_2 where abs(2) < 1) )\n" +
                "FROM\n" +
                "  t0 AS t0_2\n" +
                "GROUP BY\n" +
                "  ( CAST(t0_2.v1 AS INT) - NULL ) IN (SELECT subt0.v1  FROM  t1 " +
                "AS t1_3 RIGHT ANTI JOIN t0 subt0 ON t1_3.v5 = subt0.v1 ),\n" +
                "  t0_2.v1";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "30:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 1: v1 = 18: v1\n" +
                "  |  \n" +
                "  |----29:EXCHANGE");
    }

    @Test
    public void testHavingSubquery() throws Exception {
        String sql = "SELECT \n" +
                "  LPAD('x', 1878790738, '') \n" +
                "FROM \n" +
                "  t1 AS t1_104, \n" +
                "  t2 AS t2_105\n" +
                "GROUP BY \n" +
                "  t2_105.v7, \n" +
                "  t2_105.v8 \n" +
                "HAVING \n" +
                "  (\n" +
                "    (\n" +
                "      MIN(\n" +
                "        (t2_105.v9) IN (\n" +
                "          (\n" +
                "            SELECT \n" +
                "              t1_104.v4 \n" +
                "            FROM \n" +
                "              t1 AS t1_104 \n" +
                "            WHERE \n" +
                "              (t2_105.v8) IN ('')\n" +
                "          )\n" +
                "        )\n" +
                "      )\n" +
                "    ) IS NULL\n" +
                "  );";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "19:NESTLOOP JOIN\n" +
                "  |  join op: LEFT OUTER JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: CAST(5: v8 AS DOUBLE) = CAST('' AS DOUBLE)\n" +
                "  |  \n" +
                "  |----18:EXCHANGE\n" +
                "  |    \n" +
                "  12:HASH JOIN");
        assertContains(plan, "12:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 6: v9 = 14: v4\n" +
                "  |  other join predicates: CAST(5: v8 AS DOUBLE) = CAST('' AS DOUBLE)");
    }

    @Test
    public void testComplexInAndExistsPredicate() throws Exception {
        String sql = "select * from t0 where t0.v1 in (select v4 from t1) " +
                "or (t0.v2=0 and t0.v1 in (select v7 from t2));";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "  24:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 1: v1 = 12: v7");
        assertContains(plan, "  9:HASH JOIN\n" +
                "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 1: v1 = 16: v4");

        sql = "select * from t0 where exists (select v4 from t1) " +
                "or (t0.v2=0 and exists (select v7 from t2));";
        plan = getFragmentPlan(sql);
        assertContains(plan, "  11:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: (7: expr) OR ((2: v2 = 0) AND (12: COUNT(1) > 0))");
    }

    @Test
    public void testSubqueryReorder() throws Exception {
        String sql = "select * from t0 join t1 on t0.v3 = t1.v6 where t1.v5 > (select t2.v7 from t2);";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "  6:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: 5: v5 > 7: v7\n" +
                "  |  \n" +
                "  |----5:EXCHANGE\n" +
                "  |    \n" +
                "  1:OlapScanNode\n" +
                "     TABLE: t1");
    }

    @Test
    public void testMultiSubqueryReorder() throws Exception {
        String sql = "select * from t0 join t1 on t0.v3 = t1.v6 " +
                "where t1.v5 > (select t2.v7 from t2) and t1.v4 < (select t3.v10 from t3);";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "  15:HASH JOIN\n" +
                "  |  join op: INNER JOIN (BROADCAST)\n" +
                "  |  colocate: false, reason: \n" +
                "  |  equal join conjunct: 3: v3 = 6: v6\n" +
                "  |  \n" +
                "  |----14:EXCHANGE\n" +
                "  |    \n" +
                "  0:OlapScanNode\n" +
                "     TABLE: t0");
        assertContains(plan, "  12:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: 4: v4 < 11: v10\n" +
                "  |  \n" +
                "  |----11:EXCHANGE\n" +
                "  |    \n" +
                "  7:Project\n" +
                "  |  <slot 4> : 4: v4\n" +
                "  |  <slot 5> : 5: v5\n" +
                "  |  <slot 6> : 6: v6\n" +
                "  |  \n" +
                "  6:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: 5: v5 > 7: v7\n" +
                "  |  \n" +
                "  |----5:EXCHANGE\n" +
                "  |    \n" +
                "  1:OlapScanNode\n" +
                "     TABLE: t1");
    }

    @Test
    public void testCorrelatedScalarNonAggSubqueryByWhereClause() throws Exception {
        {
            String sql = "SELECT * FROM t0\n" +
                    "WHERE t0.v2 > (\n" +
                    "      SELECT t1.v5 FROM t1\n" +
                    "      WHERE t0.v1 = t1.v4\n" +
                    ");";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  7:Project\n" +
                    "  |  <slot 1> : 1: v1\n" +
                    "  |  <slot 2> : 2: v2\n" +
                    "  |  <slot 3> : 3: v3\n" +
                    "  |  \n" +
                    "  6:SELECT\n" +
                    "  |  predicates: 2: v2 > 7: v5\n" +
                    "  |  \n" +
                    "  5:Project\n" +
                    "  |  <slot 1> : 1: v1\n" +
                    "  |  <slot 2> : 2: v2\n" +
                    "  |  <slot 3> : 3: v3\n" +
                    "  |  <slot 7> : 9: anyValue\n" +
                    "  |  <slot 10> : assert_true((8: countRows IS NULL) OR (8: countRows <= 1))\n" +
                    "  |  \n" +
                    "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  2:AGGREGATE (update finalize)\n" +
                    "  |  output: count(1), any_value(5: v5)\n" +
                    "  |  group by: 4: v4");
        }
        {
            String sql = "SELECT * FROM t0\n" +
                    "WHERE t0.v2 > (\n" +
                    "      SELECT t1.v5 FROM t1\n" +
                    "      WHERE t0.v1 = t1.v4 and t1.v4 = 10 and t1.v5 < 2\n" +
                    ");";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  7:Project\n" +
                    "  |  <slot 1> : 1: v1\n" +
                    "  |  <slot 2> : 2: v2\n" +
                    "  |  <slot 3> : 3: v3\n" +
                    "  |  \n" +
                    "  6:SELECT\n" +
                    "  |  predicates: 2: v2 > 7: v5\n" +
                    "  |  \n" +
                    "  5:Project\n" +
                    "  |  <slot 1> : 1: v1\n" +
                    "  |  <slot 2> : 2: v2\n" +
                    "  |  <slot 3> : 3: v3\n" +
                    "  |  <slot 7> : 9: anyValue\n" +
                    "  |  <slot 10> : assert_true((8: countRows IS NULL) OR (8: countRows <= 1))\n" +
                    "  |  \n" +
                    "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  2:AGGREGATE (update finalize)\n" +
                    "  |  output: count(1), any_value(5: v5)\n" +
                    "  |  group by: 4: v4\n" +
                    "  |  \n" +
                    "  1:OlapScanNode\n" +
                    "     TABLE: t1\n" +
                    "     PREAGGREGATION: ON\n" +
                    "     PREDICATES: 4: v4 = 10, 5: v5 < 2\n" +
                    "     partitions=0/1\n" +
                    "     rollup: t1\n" +
                    "     tabletRatio=0/0\n" +
                    "     tabletList=\n" +
                    "     cardinality=0\n" +
                    "     avgRowSize=2.0\n" +
                    "     numNodes=0");
        }
        {
            connectContext.getSessionVariable().setNewPlanerAggStage(2);
            String sql = "select l.id_decimal from test_all_type l \n" +
                    "where l.id_decimal > (\n" +
                    "    select r.id_decimal from test_all_type_not_null r\n" +
                    "    where l.t1a = r.t1a\n" +
                    ");";
            String plan = getVerboseExplain(sql);
            assertContains(plan,
                    "args: DECIMAL64; result: DECIMAL64(10,2); args nullable: false; result nullable: true");
            assertContains(plan, "  7:Project\n" +
                    "  |  output columns:\n" +
                    "  |  10 <-> [10: id_decimal, DECIMAL64(10,2), true]\n" +
                    "  |  21 <-> [23: anyValue, DECIMAL64(10,2), true]");
            connectContext.getSessionVariable().setNewPlanerAggStage(0);
        }
    }

    @Test
    public void testCorrelatedScalarNonAggSubqueryBySelectClause() throws Exception {
        {
            String sql = "SELECT t0.*, (\n" +
                    "      SELECT t1.v5 FROM t1\n" +
                    "      WHERE t0.v1 = t1.v4\n" +
                    ") as t1_v5 FROM t0;";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  5:Project\n" +
                    "  |  <slot 1> : 1: v1\n" +
                    "  |  <slot 2> : 2: v2\n" +
                    "  |  <slot 3> : 3: v3\n" +
                    "  |  <slot 4> : 10: anyValue\n" +
                    "  |  <slot 11> : assert_true((9: countRows IS NULL) OR (9: countRows <= 1))\n" +
                    "  |  \n" +
                    "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 5: v4");
            assertContains(plan, "  2:AGGREGATE (update finalize)\n" +
                    "  |  output: count(1), any_value(6: v5)\n" +
                    "  |  group by: 5: v4");
        }
    }

    @Test
    public void testCorrelatedScalarNonAggSubqueryByHavingClause() throws Exception {
        {
            String sql = "SELECT v1, SUM(v2) FROM t0\n" +
                    "GROUP BY v1\n" +
                    "HAVING SUM(v2) > (\n" +
                    "      SELECT t1.v5 FROM t1\n" +
                    "      WHERE t0.v1 = t1.v4\n" +
                    ");";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  9:Project\n" +
                    "  |  <slot 1> : 1: v1\n" +
                    "  |  <slot 4> : 4: sum\n" +
                    "  |  \n" +
                    "  8:SELECT\n" +
                    "  |  predicates: 4: sum > 8: v5\n" +
                    "  |  \n" +
                    "  7:Project\n" +
                    "  |  <slot 1> : 1: v1\n" +
                    "  |  <slot 4> : 4: sum\n" +
                    "  |  <slot 8> : 10: anyValue\n" +
                    "  |  <slot 11> : assert_true((9: countRows IS NULL) OR (9: countRows <= 1))\n" +
                    "  |  \n" +
                    "  6:HASH JOIN\n" +
                    "  |  join op: RIGHT OUTER JOIN (PARTITIONED)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v4 = 1: v1");
            assertContains(plan, "  1:AGGREGATE (update finalize)\n" +
                    "  |  output: count(1), any_value(6: v5)\n" +
                    "  |  group by: 5: v4");
        }
    }

    @Test
    public void testCorrelatedScalarNonAggSubqueryWithExpression() throws Exception {
        String sql = "SELECT \n" +
                "  subt0.v1 \n" +
                "FROM \n" +
                "  (\n" +
                "    SELECT \n" +
                "      t0.v1\n" +
                "    FROM \n" +
                "      t0 \n" +
                "    WHERE \n" +
                "      (\n" +
                "          SELECT \n" +
                "            t2.v7 \n" +
                "          FROM \n" +
                "            t2 \n" +
                "          WHERE \n" +
                "            t0.v2 = 284082749\n" +
                "      ) >= 1\n" +
                "  ) subt0;";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "2:AGGREGATE (update finalize)\n" +
                "  |  output: count(1), any_value(4: v7)\n" +
                "  |  group by: \n" +
                "  |  \n" +
                "  1:OlapScanNode\n" +
                "     TABLE: t2");
        sql = "SELECT \n" +
                "  subt0.v1 \n" +
                "FROM \n" +
                "  (\n" +
                "    SELECT \n" +
                "      t0.v1\n" +
                "    FROM \n" +
                "      t0 \n" +
                "    WHERE \n" +
                "      (\n" +
                "          SELECT \n" +
                "            t2.v7 \n" +
                "          FROM \n" +
                "            t2 \n" +
                "          WHERE \n" +
                "            t0.v2 = t2.v8 + 1\n" +
                "      ) >= 1\n" +
                "  ) subt0;";
        plan = getFragmentPlan(sql);
        assertContains(plan, " 3:AGGREGATE (update finalize)\n" +
                "  |  output: count(1), any_value(4: v7)\n" +
                "  |  group by: 8: add\n" +
                "  |  \n" +
                "  2:Project\n" +
                "  |  <slot 4> : 4: v7\n" +
                "  |  <slot 8> : 5: v8 + 1");
    }

    @Test
    public void testCorrelationScalarSubqueryWithNonEQPredicate() throws Exception {
        String sql = "SELECT v1, SUM(v2) FROM t0\n" +
                "GROUP BY v1\n" +
                "HAVING SUM(v2) > (\n" +
                "      SELECT t1.v5 FROM t1\n" +
                "      WHERE nullif(false, t0.v1 < 0)\n" +
                ");";
        Assert.assertThrows("Not support Non-EQ correlation predicate correlation scalar-subquery",
                SemanticException.class, () -> getFragmentPlan(sql));
    }

    @Test
    public void testOnClauseCorrelatedScalarAggSubquery() throws Exception {
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select count(*) from t1 where t0.v2 = t1.v5)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason:");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5\n" +
                    "  |  other predicates: 1: v1 = ifnull(10: count, 0)");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on (select count(*) from t0 where t0.v2 = t1.v5) = t1.v5";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  5:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v5 = 8: v2\n" +
                    "  |  other predicates: 5: v5 = ifnull(10: count, 0)");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select count(*) from t1 where t0.v2 = t1.v5) + t1.v6";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  other join predicates: 1: v1 = 11: ifnull + 6: v6");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v3 = (select count(*) from t1 where t0.v2 = t1.v5)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5\n" +
                    "  |  other predicates: 3: v3 = ifnull(10: count, 0)");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on (select count(*) from t0 where t0.v2 = t1.v5) + t0.v3 = t1.v5";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  other join predicates: 11: ifnull + 3: v3 = 5: v5");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v5 = 8: v2");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select count(*) from t1 where t0.v2 = t1.v5 and t0.v3 = t1.v6)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5\n" +
                    "  |  equal join conjunct: 3: v3 = 9: v6\n" +
                    "  |  other predicates: 1: v1 = ifnull(10: count, 0)");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select count(*) from t1 where t0.v2 = t1.v5) " +
                    "and t1.v6 = (select max(v7) from t2 where t2.v8 = t0.v1)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  13:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 16: max = 6: v6");
            assertContains(plan, "  9:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 13: v8");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5\n" +
                    "  |  other predicates: 1: v1 = ifnull(10: count, 0)");
        }
    }

    @Test
    public void testOnClauseCorrelatedScalarNonAggSubquery() throws Exception {
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select v4 from t1 where t0.v2 = t1.v5)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  10:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v3 = (select v4 from t1 where t0.v2 = t1.v5)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  10:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select v4 from t1 where t0.v2 = t1.v5 and t1.v5 = 10 and t1.v6 != 3)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  5:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5");
            assertContains(plan, "  1:OlapScanNode\n" +
                    "     TABLE: t1\n" +
                    "     PREAGGREGATION: ON\n" +
                    "     PREDICATES: 8: v5 = 10, 9: v6 != 3\n");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on (select v1 from t0,t2 where t0.v2 = t1.v5 and t0.v2 = t2.v7) * 2 = t1.v4";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  15:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  10:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v5 = 8: v2");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v3 = (select v4 from t1 where t0.v2 = t1.v5) " +
                    "and t1.v6 = (select v8 from t2 where t2.v9 = t0.v3)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  15:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4\n" +
                    "  |  equal join conjunct: 14: v8 = 6: v6");
            assertContains(plan, "  9:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 3: v3 = 13: v9");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 8: v5");
        }
    }

    @Test
    public void testOnClauseNonCorrelatedScalarAggSubquery() throws Exception {
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select count(*) from t3 join t4)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  16:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason:");
            assertContains(plan, "  12:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 13: count");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on (select count(*) from t3) = t1.v5";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  8:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v5 = 10: count");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select count(*) from t1)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 10: count");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select count(*) from t0)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 10: count");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v3 = (select count(*) from t0)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 3: v3 = 10: count");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v3 = (select count(*) from t0) " +
                    "and t1.v5 != (select count(*) from t2)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  18:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 3: v3 = 10: count");
            assertContains(plan, "  15:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  other join predicates: 5: v5 != 15: count");
        }
    }

    @Test
    public void testOnClauseNonCorrelatedScalarNonAggSubquery() throws Exception {
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select t2.v8 from t2 where t2.v7 = 10)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 8: v8");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = (select t2.v7 + t3.v10 from t2 join t3)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  14:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  10:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 13: expr");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v3 = (select t2.v8 from t2 where t2.v7 = 10)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 3: v3 = 8: v8");
        }
        {
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v3 = (select t2.v8 from t2 where t2.v7 = 10) " +
                    "and t0.v2 = (select t2.v9 from t2 where t2.v8 < 100)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  19:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  15:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 13: v9");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 3: v3 = 8: v8");
        }
    }

    @Test
    public void testOnClauseExistentialSubquery() throws Exception {
        {
            // Uncorrelated 1
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v1 = (exists (select v7 from t2 where t2.v8 = 1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BUCKET_SHUFFLE(S))\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (PARTITIONED)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 12: cast");
        }
        {
            // Uncorrelated 2
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 = (exists (select v7 from t2 where t2.v8 = 1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  10:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  6:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 12: cast");
        }
        {
            // Uncorrelated 3, multi subqueries
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 = (not exists (select v7 from t2 where t2.v8 = 1)) " +
                    "and t1.v4 = (exists(select v10 from t3 where t3.v11 < 5))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  17:NESTLOOP JOIN\n" +
                    "  |  join op: INNER JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  6:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 18: cast");
            assertContains(plan, "  14:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 4: v4 = 17: cast");
        }
        {
            // correlated 1
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v1 = (exists (select v7 from t2 where t2.v8 = t1.v5))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 4: v4 = 1: v1\n" +
                    "  |  equal join conjunct: 11: cast = 1: v1");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v5 = 8: v8\n" +
                    "  |  other predicates: CAST(8: v8 IS NOT NULL AS BIGINT) IS NOT NULL");
        }
        {
            // correlated 2
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 = (exists (select v7 from t2 where t2.v8 = t1.v5))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  8:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 11: cast = 1: v1");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v5 = 8: v8\n" +
                    "  |  other predicates: CAST(8: v8 IS NOT NULL AS BIGINT) IS NOT NULL");
        }
        {
            // correlated 3, multi subqueries
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 = (exists (select v7 from t2 where t2.v8 = t1.v5)) " +
                    "and t0.v2 = (not exists(select v11 from t3 where t3.v10 = t0.v1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  13:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 15: cast = 1: v1");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 5: v5 = 8: v8\n" +
                    "  |  other predicates: CAST(8: v8 IS NOT NULL AS BIGINT) IS NOT NULL");
            assertContains(plan, "  10:HASH JOIN\n" +
                    "  |  join op: LEFT OUTER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 11: v10\n" +
                    "  |  other predicates: 2: v2 = CAST(11: v10 IS NULL AS BIGINT)");
        }
    }

    @Test
    public void testOnClauseQuantifiedSubquery() throws Exception {
        {
            // Uncorrelated 1
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v1 in (select v7 from t2 where t2.v8 = 1)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  9:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BUCKET_SHUFFLE(S))\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  5:HASH JOIN\n" +
                    "  |  join op: LEFT SEMI JOIN (PARTITIONED)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 7: v7");
        }
        {
            // Uncorrelated 2
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 in (select v7 from t2)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  7:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  3:HASH JOIN\n" +
                    "  |  join op: LEFT SEMI JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 7: v7");
        }
        {
            // Uncorrelated 3, multi subqueries
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 not in (select v7 from t2) " +
                    "and t0.v2 in (select v8 from t2)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN\n" +
                    "  |  colocate: false, reason:");
            assertContains(plan, "  8:HASH JOIN\n" +
                    "  |  join op: LEFT SEMI JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 12: v8");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: NULL AWARE LEFT ANTI JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 7: v7");
        }
        {
            // correlated 1
            String sql = "select * from t0 " +
                    "join t1 on t0.v1 = t1.v4 " +
                    "and t0.v1 in (select v7 from t2 where t2.v9 = t0.v3)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  7:HASH JOIN\n" +
                    "  |  join op: INNER JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 4: v4");
            assertContains(plan, "  3:HASH JOIN\n" +
                    "  |  join op: LEFT SEMI JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 7: v7\n" +
                    "  |  equal join conjunct: 3: v3 = 9: v9");
        }
        {
            // correlated 2
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 in (select v7 from t2 where t2.v9 = t0.v3)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  7:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN\n" +
                    "  |  colocate: false, reason:");
            assertContains(plan, "  3:HASH JOIN\n" +
                    "  |  join op: LEFT SEMI JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 7: v7\n" +
                    "  |  equal join conjunct: 3: v3 = 9: v9");
        }
        {
            // correlated 3, multi subqueries
            String sql = "select * from t0 " +
                    "join t1 on " +
                    "t0.v1 in (select v7 from t2 where t2.v9 = t0.v3) " +
                    "and t0.v2 not in (select v8 from t2 where t2.v9 = t0.v2)";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  11:NESTLOOP JOIN\n" +
                    "  |  join op: CROSS JOIN\n" +
                    "  |  colocate: false, reason: ");
            assertContains(plan, "  8:HASH JOIN\n" +
                    "  |  join op: NULL AWARE LEFT ANTI JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 2: v2 = 12: v8\n" +
                    "  |  equal join conjunct: 2: v2 = 13: v9");
            assertContains(plan, "  4:HASH JOIN\n" +
                    "  |  join op: LEFT SEMI JOIN (BROADCAST)\n" +
                    "  |  colocate: false, reason: \n" +
                    "  |  equal join conjunct: 1: v1 = 7: v7\n" +
                    "  |  equal join conjunct: 3: v3 = 9: v9");
        }
    }

    @Test
    public void testOnClauseNotSupportedCases() {
        assertExceptionMessage("select * from t0 " +
                        "join t1 on (select v1 from t0 where t0.v2 = t1.v5) = (select v4 from t1 where t0.v2 = t1.v5)",
                "Not support ON Clause conjunct contains more than one subquery");

        assertExceptionMessage("select * from t0 " +
                        "join t1 on t0.v1 + t1.v4 = (select count(*) from t0)",
                "Not support ON Clause un-correlated subquery referencing columns of more than one table");

        assertExceptionMessage("select * from t0 " +
                        "join t1 on 1 = (select count(*) from t2 where t0.v1 = t2.v7 and t1.v4 = t2.v7)",
                "Not support ON Clause correlated subquery referencing columns of more than one table");

        assertExceptionMessage("select * from t0 " +
                        "join t1 on 1 = (select count(*) from t2 where t0.v1 = t2.v7 and t1.v4 = t2.v7)",
                "Not support ON Clause correlated subquery referencing columns of more than one table");

        assertExceptionMessage("select * from t0 " +
                        "join t1 on t0.v1 in (select t2.v7 from t2 where t1.v5 = t2.v8)",
                "Not support ON Clause correlated in-subquery referencing columns of more than one table");
    }

    @Test
    public void testWherePredicateSubqueryElimination() throws Exception {
        {
            String sql = "select * from t0 where 1 < 1 and v2 = (select v4 from t1);";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select * from t0 where 1 = 1 or v2 = (select v4 from t1);";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select * from t0 where v1 = 'a' or (1 = 2 and v2 = (select v4 from t1));";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select * from t0 where v1 = 'a' and (1 < 2 or v2 = (select v4 from t1));";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql =
                    "select * from t0 where v1 = 'a' and (1 < 2 or v2 = (select v4 from t1)) " +
                            "and exists(select v5 from t1 where t0.v3 = t1.v6);";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "v3");
            assertContains(plan, "v6");
            assertNotContains(plan, "v4");
        }
        {
            String sql =
                    "select * from t0 where v1 = 'a' and (1 < 2 and v2 = (select v4 from t1)) " +
                            "and ( 2 != 3  or exists(select v5 from t1 where t0.v3 = t1.v6));";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "v2");
            assertContains(plan, "v4");
            assertNotContains(plan, "v5");
            assertNotContains(plan, "v6");
        }
    }

    @Test
    public void testOnPredicateSubqueryElimination() throws Exception {
        {
            String sql = "select * from t0 join t3 " +
                    "on 1 < 1 and v2 = (select v4 from t1);";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select * from t0 join t3 " +
                    "on 1 = 1 or v2 = (select v4 from t1);";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select * from t0 join t3 " +
                    "on v1 = 'a' or (1 = 2 and v2 = (select v4 from t1));";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select * from t0 join t3 " +
                    "on v1 = 'a' and (1 < 2 or v2 = (select v4 from t1));";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql =
                    "select * from t0 where v1 = 'a' and (1 < 2 or v2 = (select v4 from t1)) " +
                            "and exists(select v5 from t1 where t0.v3 = t1.v6);";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "v3");
            assertContains(plan, "v6");
            assertNotContains(plan, "v4");
        }
        {
            String sql =
                    "select * from t0 where v1 = 'a' and (1 < 2 and v2 = (select v4 from t1)) " +
                            "and ( 2 != 3  or exists(select v5 from t1 where t0.v3 = t1.v6));";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "v2");
            assertContains(plan, "v4");
            assertNotContains(plan, "v5");
            assertNotContains(plan, "v6");
        }
    }

    @Test
    public void testHavingPredicateSubqueryElimination() throws Exception {
        {
            String sql = "select v1 from t0 group by v1 " +
                    "having 1 < 1 and sum(v2) = (select v4 from t1);";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select v1 from t0 group by v1 " +
                    "having 1 = 1 or sum(v2) = (select v4 from t1);";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select v1 from t0 group by v1 " +
                    "having v1 = 'a' or (1 = 2 and max(v2) = (select v4 from t1));";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select v1 from t0 group by v1 " +
                    "having v1 = 'a' and (1 < 2 or avg(v2) = (select v4 from t1));";
            String plan = getFragmentPlan(sql);
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select v1 from t0 group by v1 " +
                    "having v1 = 'a' and (1 < 2 or max(v2) = (select v4 from t1)) " +
                    "and max(v3) = (select max(v5) from t1 where t0.v1 = t1.v6);";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "v3");
            assertContains(plan, "v6");
            assertNotContains(plan, "v4");
        }
        {
            String sql = "select v1 from t0 group by v1 " +
                    "having v1 = 'a' and (1 < 2 and max(v2) = (select v4 from t1)) " +
                    "and ( 2 != 3  or exists(select v5 from t1 where t0.v1 = t1.v6));";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "v2");
            assertContains(plan, "v4");
            assertNotContains(plan, "v5");
            assertNotContains(plan, "v6");
        }
    }

    @Test
    public void testPushDownAssertProject() throws Exception {
        String sql = "select (select 1 from t2) from t0";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "  STREAM DATA SINK\n" +
                "    EXCHANGE ID: 03\n" +
                "    UNPARTITIONED\n" +
                "\n" +
                "  2:Project\n" +
                "  |  <slot 8> : 1\n" +
                "  |  \n" +
                "  1:OlapScanNode");
    }

    @Test
    public void testSubqueryTypeRewrite() throws Exception {
        {
            String sql =
                    "select nullif((select max(v4) from t1), (select min(v6) from t1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  14:Project\n" +
                    "  |  <slot 2> : nullif(7: max, 11: min)");
        }
        {
            String sql =
                    "select nullif((select max(v4) from t1), (select avg(v6) from t1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  14:Project\n" +
                    "  |  <slot 2> : nullif(CAST(7: max AS DOUBLE), 11: avg)");
        }
        {
            String sql =
                    "select ifnull((select max(v4) from t1), (select min(v6) from t1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  14:Project\n" +
                    "  |  <slot 2> : ifnull(7: max, 11: min)");
        }
        {
            String sql =
                    "select ifnull((select max(v4) from t1), (select avg(v6) from t1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  14:Project\n" +
                    "  |  <slot 2> : ifnull(CAST(7: max AS DOUBLE), 11: avg)");
        }
        {
            String sql =
                    "select ifnull((select max(v4) from t1), (select min(v6) from t1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  14:Project\n" +
                    "  |  <slot 2> : ifnull(7: max, 11: min)");
        }
        {
            String sql =
                    "select coalesce((select max(v4) from t1), (select any_value(v5) from t1), (select min(v6) from t1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  21:Project\n" +
                    "  |  <slot 2> : coalesce(7: max, 12: any_value, 16: min)");
        }
        {
            String sql =
                    "select coalesce((select max(v4) from t1), (select avg(v5) from t1), (select min(v6) from t1))";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  21:Project\n" +
                    "  |  <slot 2> : coalesce(CAST(7: max AS DOUBLE), 12: avg, CAST(16: min AS DOUBLE))");
        }
        {
            String sql =
                    "select case " +
                            "when(select count(*) from t2) > 10 then (select max(v4) from t1) " +
                            "else (select min(v6) from t1) " +
                            "end c";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  21:Project\n" +
                    "  |  <slot 2> : if(7: count > 10, 12: max, 16: min)");
        }
        {
            String sql =
                    "select case " +
                            "when(select count(*) from t2) > 10 then (select max(v4) from t1) " +
                            "else (select avg(v6) from t1) " +
                            "end c";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  21:Project\n" +
                    "  |  <slot 2> : if(7: count > 10, CAST(12: max AS DOUBLE), 16: avg)");
        }
        {
            String sql =
                    "select case " +
                            "when(select count(*) from t2) > 10 then (select max(v4) from t1) " +
                            "when(select count(*) from t3) > 20 then (select any_value(v5) from t1) " +
                            "else (select min(v6) from t1) " +
                            "end c";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  35:Project\n" +
                    "  |  <slot 2> : CASE WHEN 7: count > 10 THEN 12: max WHEN 17: count > 20 " +
                    "THEN 22: any_value ELSE 26: min END");
        }
        {
            String sql =
                    "select case " +
                            "when(select count(*) from t2) > 10 then (select max(v4) from t1) " +
                            "when(select count(*) from t3) > 20 then (select avg(v5) from t1) " +
                            "else (select min(v6) from t1) " +
                            "end c";
            String plan = getFragmentPlan(sql);
            assertContains(plan, "  35:Project\n" +
                    "  |  <slot 2> : CASE WHEN 7: count > 10 THEN CAST(12: max AS DOUBLE) " +
                    "WHEN 17: count > 20 THEN 22: avg ELSE CAST(26: min AS DOUBLE) END");
        }
    }

    @Test
    public void testSubqueryTypeCast() throws Exception {
        String sql = "select * from test_all_type where t1a like (select t1a from test_all_type_not_null);";
        String plan = getFragmentPlan(sql);
        assertContains(plan, "5:NESTLOOP JOIN\n" +
                "  |  join op: CROSS JOIN\n" +
                "  |  colocate: false, reason: \n" +
                "  |  other join predicates: 1: t1a LIKE 11: t1a");
    }
}
