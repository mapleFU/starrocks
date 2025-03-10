# StarRocks

StarRocks is a next-gen, high-performance analytical data warehouse that enables real-time, multi-dimensional, and highly concurrent data analysis. StarRocks has an MPP architecture and it is equipped with a fully vectorized execution engine, a columnar storage engine that supports real-time updates, and is powered by a rich set of features including a fully-customized cost-based optimizer (CBO), intelligent materialized view and more. StarRocks is also compatible with MySQL protocols and can be easily connected using MySQL clients and popular BI tools. StarRocks is highly scalable, available, and easy to maintain. It is widely adopted in the industry, powering a variety of OLAP scenarios, such as real-time analytics, ad-hoc queries, data lake analytics and more.

Join our [Slack channel](https://join.slack.com/t/starrocks/shared_invite/zt-z5zxqr0k-U5lrTVlgypRIV8RbnCIAzg) for live support, discussion, or latest community news. You can also follow us on [LinkedIn](https://www.linkedin.com/company/starrocks) to get first-hand updates on new features, events, and sharing.

<NavBox>
<NavBoxPart title="About StarRocks">
<NavBoxPartItem>

- [Introduction](../introduction/what_is_starrocks.md)
- [Concepts](../quick_start/Concepts.md)
- [Architecture](../quick_start/Architecture.md)

</NavBoxPartItem>
</NavBoxPart>

<NavBoxPart title="Get started​">
<NavBoxPartItem>

- [Deploy StarRocks](../quick_start/Deploy.md)
- [Ingest and query data](../quick_start/Import_and_query.md)

</NavBoxPartItem>
</NavBoxPart>
</NavBox>

<NavBox>
<NavBoxPart title="Table design ​">
<NavBoxPartItem>

- [Overview of table design](../table_design/StarRocks_table_design.md)
- [Data models](../table_design/Data_model.md)
- [Data distribution](../table_design/Data_distribution.md)
- [Sort key and prefix index](../table_design/Sort_key.md)

</NavBoxPartItem>
</NavBoxPart>

<NavBoxPart title="Ingestion​">
<NavBoxPartItem>

- [Overview of ingestion](../loading/Loading_intro.md)
- [Ingestion via HTTP](../loading/StreamLoad.md)
- [Batch ingestion from HDFS or cloud object storage](../loading/BrokerLoad.md)
- [Continuous ingestion from Apache Kafka®](../loading/RoutineLoad.md)
- [Bulk ingestion and data transformation using Apache Spark™](../loading/SparkLoad.md)
- [Real-time synchronization from MySQL](../loading/Flink_cdc_load.md)

</NavBoxPartItem>
</NavBoxPart>
</NavBox>

<NavBox>
<NavBoxPart title="Querying​">
<NavBoxPartItem title="Query acceleration">

- [Cost-based optimizer](../using_starrocks/Cost_based_optimizer.md)
- [Materialized view](../using_starrocks/Materialized_view.md)
- [Colocate Join](../using_starrocks/Colocate_join.md)

</NavBoxPartItem>
<NavBoxPartItem title="Query semi-structured data">

- [JSON](../sql-reference/sql-statements/data-types/JSON.md)
- [ARRAY](../using_starrocks/Array.md)

</NavBoxPartItem>
</NavBoxPart>

<NavBoxPart>
<NavBoxPartItem title="Query external data sources​">

- [Apache Hive™](../using_starrocks/External_table#hive-external-table.md)
- [Apache Hudi](../using_starrocks/External_table#hudi-external-table.md)
- [Apache Iceberg](../using_starrocks/External_table#apache-iceberg-external-table.md)
- [MySQL](../using_starrocks/External_table#mysql-external-table.md)
- [Elasticsearch](../using_starrocks/External_table#elasticsearch-external-table.md)
- [JDBC-compatible database](../using_starrocks/External_table#external-table-for-a-jdbc-compatible-database.md)

</NavBoxPartItem>
</NavBoxPart>
</NavBox>

<NavBox>
<NavBoxPart title="Administration">
<NavBoxPartItem>

- [Manage a cluster](../administration/Cluster_administration.md)
- [Scale in and out a cluster](../administration/Scale_up_down.md)
- [Tune query performance](../administration/Query_planning.md)
- [Manage workloads](../administration//resource_group.md)

</NavBoxPartItem>
</NavBoxPart>

<NavBoxPart title="References​">
<NavBoxPartItem>

- [SQL reference](../sql-reference/sql-statements/account-management/ALTER%20USER.md)
- [Function reference](../sql-reference/sql-functions/date-time-functions/convert_tz.md)

</NavBoxPartItem>
</NavBoxPart>
</NavBox>

<NavBox>
<NavBoxPart title="FAQ​">
<NavBoxPartItem>

- [Ingestion and export](../faq/loading/Loading_faq.md)
- [Deployment](../faq/Deploy_faq.md)
- [SQL](../faq/Sql_faq.md)

</NavBoxPartItem>
</NavBoxPart>

<NavBoxPart title="Benchmarks​">
<NavBoxPartItem>

- [SSB benchmark](../benchmarking/SSB_Benchmarking.md)
- [TPC-H benchmark](../benchmarking/TPC-H_Benchmarking.md)

</NavBoxPartItem>
</NavBoxPart>
</NavBox>
