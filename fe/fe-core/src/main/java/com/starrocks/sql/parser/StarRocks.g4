// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Inc.

grammar StarRocks;
import StarRocksLex;

sqlStatements
    : (singleStatement (SEMICOLON EOF? | EOF))+
    ;

singleStatement
    : statement
    ;

statement
    // Query Statement
    : queryStatement

    // Database Statement
    | useDatabaseStatement
    | useCatalogStatement
    | showDatabasesStatement
    | alterDbQuotaStmtatement
    | createDbStatement
    | dropDbStatement
    | showCreateDbStatement
    | alterDatabaseRename
    | recoverDbStmt
    | showDataStmt

    // Table Statement
    | createTableStatement
    | createTableAsSelectStatement
    | createTableLikeStatement
    | showCreateTableStatement
    | dropTableStatement
    | recoverTableStatement
    | truncateTableStatement
    | showTableStatement
    | descTableStatement
    | showTableStatusStatement
    | showColumnStatement
    | refreshTableStatement
    | alterTableStatement
    | cancelAlterTableStatement
    | showAlterStatement

    // View Statement
    | createViewStatement
    | alterViewStatement
    | dropViewStatement

    // Partition Statement
    | showPartitionsStatement
    | recoverPartitionStatement

    // Index Statement
    | createIndexStatement
    | dropIndexStatement
    | showIndexStatement

    // Task Statement
    | submitTaskStatement

    // Materialized View Statement
    | createMaterializedViewStatement
    | showMaterializedViewStatement
    | dropMaterializedViewStatement
    | alterMaterializedViewStatement
    | refreshMaterializedViewStatement
    | cancelRefreshMaterializedViewStatement

    // Catalog Statement
    | createExternalCatalogStatement
    | dropExternalCatalogStatement
    | showCatalogsStatement

    // DML Statement
    | insertStatement
    | updateStatement
    | deleteStatement

    //Routine Statement
    | stopRoutineLoadStatement
    | resumeRoutineLoadStatement
    | pauseRoutineLoadStatement
    | showRoutineLoadStatement
    | showRoutineLoadTaskStatement

    // Admin Statement
    | adminSetConfigStatement
    | adminSetReplicaStatusStatement
    | adminShowConfigStatement
    | adminShowReplicaDistributionStatement
    | adminShowReplicaStatusStatement
    | adminRepairTableStatement
    | adminCancelRepairTableStatement
    | adminCheckTabletsStatement

    // Cluster Mangement Statement
    | alterSystemStatement
    | showNodesStatement

    // Analyze Statement
    | analyzeStatement
    | dropStatsStatement
    | createAnalyzeStatement
    | dropAnalyzeJobStatement
    | analyzeHistogramStatement
    | dropHistogramStatement
    | showAnalyzeStatement
    | showStatsMetaStatement
    | showHistogramMetaStatement
    | killAnalyzeStatement

    // Work Group Statement
    | createResourceGroupStatement
    | dropResourceGroupStatement
    | alterResourceGroupStatement
    | showResourceGroupStatement

    // Extenal Resource Statement
    | createResourceStatement
    | alterResourceStatement
    | dropResourceStatement
    | showResourceStatement

    //UDF
    | showFunctionsStatement
    | dropFunctionStatement
    | createFunctionStatement

    // Load Statement
    | loadStatement
    | showLoadStatement
    | showLoadWarningsStatement
    | cancelLoadStatement

    //Show Statement
    | showAuthorStatement
    | showBackendsStatement
    | showBrokerStatement
    | showCharsetStatement
    | showCollationStatement
    | showDeleteStatement
    | showDynamicPartitionStatement
    | showEventsStatement
    | showEnginesStatement
    | showFrontendsStatement
    | showPluginsStatement
    | showRepositoriesStatement
    | showOpenTableStatement
    | showProcedureStatement
    | showProcStatement
    | showProcesslistStatement
    | showStatusStatement
    | showTabletStatement
    | showTriggersStatement
    | showUserStatement
    | showUserPropertyStatement
    | showVariablesStatement
    | showWarningStatement

    // privilege
    | grantRoleStatement
    | revokeRoleStatement
    | executeAsStatement
    | alterUserStatement
    | createUserStatement
    | dropUserStatement
    | showAuthenticationStatement
    | createRoleStatement
    | grantPrivilegeStatement
    | revokePrivilegeStatement
    | showRolesStatement
    | showGrantsStatement
    | dropRoleStatement


    // Backup Restore Satement
    | backupStatement
    | showBackupStatement
    | restoreStatement
    | showRestoreStatement

    // Snapshot Satement
    | showSnapshotStatement

    // Other statement
    | killStatement
    | setUserPropertyStatement
    | setStatement
    ;

// ---------------------------------------- DataBase Statement ---------------------------------------------------------

useDatabaseStatement
    : USE qualifiedName
    ;

useCatalogStatement
    : USE CATALOG identifierOrString
    ;

alterDbQuotaStmtatement
    : ALTER DATABASE identifier SET DATA QUOTA identifier
    | ALTER DATABASE identifier SET REPLICA QUOTA INTEGER_VALUE
    ;

createDbStatement
    : CREATE (DATABASE | SCHEMA) (IF NOT EXISTS)? identifier
    ;

dropDbStatement
    : DROP (DATABASE | SCHEMA) (IF EXISTS)? identifier FORCE?
    ;

showCreateDbStatement
    : SHOW CREATE (DATABASE | SCHEMA) identifier
    ;


alterDatabaseRename
    : ALTER DATABASE identifier RENAME identifier
    ;


recoverDbStmt
    : RECOVER (DATABASE | SCHEMA) identifier
    ;

showDataStmt
    : SHOW DATA
    | SHOW DATA FROM qualifiedName
    ;

// ------------------------------------------- Table Statement ---------------------------------------------------------

createTableStatement
    : CREATE EXTERNAL? TABLE (IF NOT EXISTS)? qualifiedName
          '(' columnDesc (',' columnDesc)* (',' indexDesc)* ')'
          engineDesc?
          charsetDesc?
          keyDesc?
          comment?
          partitionDesc?
          distributionDesc?
          rollupDesc?
          properties?
          extProperties?
     ;

columnDesc
    : identifier type charsetName? KEY? aggDesc? (NULL | NOT NULL)? defaultDesc? comment?
    ;

charsetName
    : CHAR SET identifier
    | CHARSET identifier
    ;

defaultDesc
    : DEFAULT (string| NULL | CURRENT_TIMESTAMP)
    ;

indexDesc
    : INDEX indexName=identifier identifierList indexType? comment?
    ;

engineDesc
    : ENGINE EQ identifier
    ;

charsetDesc
    : DEFAULT? CHARSET EQ? identifierOrString
    ;


keyDesc
    : (AGGREGATE | UNIQUE | PRIMARY | DUPLICATE) KEY identifierList
    ;

aggDesc
    : SUM
    | MAX
    | MIN
    | REPLACE
    | HLL_UNION
    | BITMAP_UNION
    | PERCENTILE_UNION
    | REPLACE_IF_NOT_NULL
    ;

rollupDesc
    : ROLLUP '(' rollupItem (',' rollupItem)* ')'
    ;

rollupItem
    : rollupName=identifier identifierList (dupKeys)? (fromRollup)? properties?
    ;

dupKeys
    : DUPLICATE KEY identifierList
    ;

fromRollup
    : FROM identifier
    ;

createTableAsSelectStatement
    : CREATE TABLE (IF NOT EXISTS)? qualifiedName
        ('(' identifier (',' identifier)* ')')? comment?
        partitionDesc?
        distributionDesc?
        properties?
        AS queryStatement
        ;

dropTableStatement
    : DROP TABLE (IF EXISTS)? qualifiedName FORCE?
    ;

alterTableStatement
    : ALTER TABLE qualifiedName alterClause (',' alterClause)*
    | ALTER TABLE qualifiedName ADD ROLLUP rollupItem (',' rollupItem)*
    | ALTER TABLE qualifiedName DROP ROLLUP identifier (',' identifier)*
    ;

createIndexStatement
    : CREATE INDEX indexName=identifier
        ON qualifiedName identifierList indexType?
        comment?
    ;

dropIndexStatement
    : DROP INDEX indexName=identifier ON qualifiedName
    ;

indexType
    : USING BITMAP
    ;

showTableStatement
    : SHOW FULL? TABLES ((FROM | IN) db=qualifiedName)? ((LIKE pattern=string) | (WHERE expression))?
    ;

showCreateTableStatement
    : SHOW CREATE (TABLE | VIEW | MATERIALIZED VIEW) table=qualifiedName
    ;

showColumnStatement
    : SHOW FULL? COLUMNS ((FROM | IN) table=qualifiedName) ((FROM | IN) db=qualifiedName)?
        ((LIKE pattern=string) | (WHERE expression))?
    ;

showTableStatusStatement
    : SHOW TABLE STATUS ((FROM | IN) db=qualifiedName)? ((LIKE pattern=string) | (WHERE expression))?
    ;

refreshTableStatement
    : REFRESH EXTERNAL TABLE qualifiedName (PARTITION '(' string (',' string)* ')')?
    ;

showAlterStatement
    : SHOW ALTER TABLE (COLUMN | ROLLUP) ((FROM | IN) db=qualifiedName)?
        (WHERE expression)? (ORDER BY sortItem (',' sortItem)*)? (limitElement)?
    | SHOW ALTER MATERIALIZED VIEW ((FROM | IN) db=qualifiedName)?
              (WHERE expression)? (ORDER BY sortItem (',' sortItem)*)? (limitElement)?
    ;

descTableStatement
    : (DESC | DESCRIBE) table=qualifiedName ALL?
    ;

createTableLikeStatement
    : CREATE (EXTERNAL)? TABLE (IF NOT EXISTS)? qualifiedName LIKE qualifiedName
    ;

showIndexStatement
    : SHOW (INDEX | INDEXES | KEY | KEYS) ((FROM | IN) table=qualifiedName) ((FROM | IN) db=qualifiedName)?
    ;

recoverTableStatement
    : RECOVER TABLE qualifiedName
    ;

truncateTableStatement
    : TRUNCATE TABLE qualifiedName partitionNames?
    ;

cancelAlterTableStatement
    : CANCEL ALTER TABLE (COLUMN | ROLLUP)? FROM qualifiedName ('(' INTEGER_VALUE (',' INTEGER_VALUE)* ')')?
    | CANCEL ALTER MATERIALIZED VIEW FROM qualifiedName
    ;

showPartitionsStatement
    : SHOW TEMPORARY? PARTITIONS FROM table=qualifiedName
    (WHERE expression)?
    (ORDER BY sortItem (',' sortItem)*)? limitElement?
    ;

recoverPartitionStatement
    : RECOVER PARTITION identifier FROM table=qualifiedName
    ;

// ------------------------------------------- View Statement ----------------------------------------------------------

createViewStatement
    : CREATE VIEW (IF NOT EXISTS)? qualifiedName
        ('(' columnNameWithComment (',' columnNameWithComment)* ')')?
        comment? AS queryStatement
    ;

alterViewStatement
    : ALTER VIEW qualifiedName
    ('(' columnNameWithComment (',' columnNameWithComment)* ')')?
    AS queryStatement
    ;

dropViewStatement
    : DROP VIEW (IF EXISTS)? qualifiedName
    ;

// ------------------------------------------- Task Statement ----------------------------------------------------------

submitTaskStatement
    : SUBMIT setVarHint* TASK qualifiedName?
    AS createTableAsSelectStatement
    ;

// ------------------------------------------- Materialized View Statement ---------------------------------------------

createMaterializedViewStatement
    : CREATE MATERIALIZED VIEW (IF NOT EXISTS)? mvName=qualifiedName
    comment?
    (PARTITION BY primaryExpression)?
    distributionDesc?
    refreshSchemeDesc?
    properties?
    AS queryStatement
    ;

showMaterializedViewStatement
    : SHOW MATERIALIZED VIEW ((FROM | IN) db=qualifiedName)? ((LIKE pattern=string) | (WHERE expression))?
    ;

dropMaterializedViewStatement
    : DROP MATERIALIZED VIEW (IF EXISTS)? mvName=qualifiedName
    ;

alterMaterializedViewStatement
    : ALTER MATERIALIZED VIEW mvName=qualifiedName (refreshSchemeDesc | tableRenameClause)
    ;

refreshMaterializedViewStatement
    : REFRESH MATERIALIZED VIEW mvName=qualifiedName
    ;

cancelRefreshMaterializedViewStatement
    : CANCEL REFRESH MATERIALIZED VIEW mvName=qualifiedName
    ;

// ------------------------------------------- Admin Statement ---------------------------------------------------------

adminSetConfigStatement
    : ADMIN SET FRONTEND CONFIG '(' property ')'
    ;
adminSetReplicaStatusStatement
    : ADMIN SET REPLICA STATUS properties
    ;
adminShowConfigStatement
    : ADMIN SHOW FRONTEND CONFIG (LIKE pattern=string)?
    ;

adminShowReplicaDistributionStatement
    : ADMIN SHOW REPLICA DISTRIBUTION FROM qualifiedName partitionNames?
    ;

adminShowReplicaStatusStatement
    : ADMIN SHOW REPLICA STATUS FROM qualifiedName partitionNames? (WHERE where=expression)?
    ;

adminRepairTableStatement
    : ADMIN REPAIR TABLE qualifiedName partitionNames?
    ;

adminCancelRepairTableStatement
    : ADMIN CANCEL REPAIR TABLE qualifiedName partitionNames?
    ;

adminCheckTabletsStatement
    : ADMIN CHECK tabletList properties
    ;

// ------------------------------------------- Cluster Mangement Statement ---------------------------------------------

alterSystemStatement
    : ALTER SYSTEM alterClause
    ;

// ------------------------------------------- Catalog Statement -------------------------------------------------------

createExternalCatalogStatement
    : CREATE EXTERNAL CATALOG catalogName=identifierOrString comment? properties
    ;

dropExternalCatalogStatement
    : DROP CATALOG catalogName=identifierOrString
    ;

showCatalogsStatement
    : SHOW CATALOGS
    ;


// ------------------------------------------- Alter Clause ------------------------------------------------------------

alterClause
    //Alter system clause
    : addFrontendClause
    | dropFrontendClause
    | modifyFrontendHostClause
    | addBackendClause
    | dropBackendClause
    | decommissionBackendClause
    | modifyBackendHostClause
    | addComputeNodeClause
    | dropComputeNodeClause
    | modifyBrokerClause
    | alterLoadErrorUrlClause

    //Alter table clause
    | createIndexClause
    | dropIndexClause
    | tableRenameClause
    | swapTableClause
    | modifyTablePropertiesClause
    | addColumnClause
    | addColumnsClause
    | dropColumnClause
    | modifyColumnClause
    | columnRenameClause
    | reorderColumnsClause
    | rollupRenameClause

    //Alter partition clause
    | addPartitionClause
    | dropPartitionClause
    | distributionClause
    | truncatePartitionClause
    | modifyPartitionClause
    | replacePartitionClause
    | partitionRenameClause
    ;

// ---------Alter system clause---------

addFrontendClause
   : ADD (FOLLOWER | OBSERVER) string
   ;

dropFrontendClause
   : DROP (FOLLOWER | OBSERVER) string
   ;

modifyFrontendHostClause
  : MODIFY FRONTEND HOST string TO string
  ;

addBackendClause
   : ADD BACKEND string (',' string)*
   ;

dropBackendClause
   : DROP BACKEND string (',' string)* FORCE?
   ;

decommissionBackendClause
   : DECOMMISSION BACKEND string (',' string)*
   ;

modifyBackendHostClause
   : MODIFY BACKEND HOST string TO string
   ;

addComputeNodeClause
   : ADD COMPUTE NODE string (',' string)*
   ;

dropComputeNodeClause
   : DROP COMPUTE NODE string (',' string)*
   ;

modifyBrokerClause
    : ADD BROKER identifierOrString string (',' string)*
    | DROP BROKER identifierOrString string (',' string)*
    | DROP ALL BROKER identifierOrString
    ;

alterLoadErrorUrlClause
    : SET LOAD ERRORS HUB properties?
    ;

// ---------Alter table clause---------

createIndexClause
    : ADD INDEX indexName=identifier identifierList indexType? comment?
    ;

dropIndexClause
    : DROP INDEX indexName=identifier
    ;

tableRenameClause
    : RENAME identifier
    ;

swapTableClause
    : SWAP WITH identifier
    ;

modifyTablePropertiesClause
    : SET propertyList
    ;

addColumnClause
    : ADD COLUMN columnDesc (FIRST | AFTER identifier)? ((TO | IN) rollupName=identifier)? properties?
    ;

addColumnsClause
    : ADD COLUMN '(' columnDesc (',' columnDesc)* ')' ((TO | IN) rollupName=identifier)? properties?
    ;

dropColumnClause
    : DROP COLUMN identifier (FROM rollupName=identifier)? properties?
    ;

modifyColumnClause
    : MODIFY COLUMN columnDesc (FIRST | AFTER identifier)? (FROM rollupName=identifier)? properties?
    ;

columnRenameClause
    : RENAME COLUMN oldColumn=identifier newColumn=identifier
    ;

reorderColumnsClause
    : ORDER BY identifierList (FROM rollupName=identifier)? properties?
    ;

rollupRenameClause
    : RENAME ROLLUP rollupName=identifier newRollupName=identifier
    ;

// ---------Alter partition clause---------

addPartitionClause
    : ADD TEMPORARY? (singleRangePartition | PARTITIONS multiRangePartition) distributionDesc? properties?
    ;

dropPartitionClause
    : DROP TEMPORARY? PARTITION (IF EXISTS)? identifier FORCE?
    ;

truncatePartitionClause
    : TRUNCATE partitionNames
    ;

modifyPartitionClause
    : MODIFY PARTITION (identifier | identifierList | '(' ASTERISK_SYMBOL ')') SET propertyList
    | MODIFY PARTITION distributionDesc
    ;

replacePartitionClause
    : REPLACE parName=partitionNames WITH tempParName=partitionNames properties?
    ;

partitionRenameClause
    : RENAME PARTITION parName=identifier newParName=identifier
    ;

// ------------------------------------------- DML Statement -----------------------------------------------------------

insertStatement
    : explainDesc? INSERT (INTO | OVERWRITE) qualifiedName partitionNames?
        (WITH LABEL label=identifier)? columnAliases?
        (queryStatement | (VALUES expressionsWithDefault (',' expressionsWithDefault)*))
    ;

updateStatement
    : explainDesc? UPDATE qualifiedName SET assignmentList (WHERE where=expression)?
    ;

deleteStatement
    : explainDesc? DELETE FROM qualifiedName partitionNames? (WHERE where=expression)?
    ;

// ------------------------------------------- Routine Statement -----------------------------------------------------------

stopRoutineLoadStatement
    : STOP ROUTINE LOAD FOR (db=qualifiedName '.')? name=identifier
    ;

resumeRoutineLoadStatement
    : RESUME ROUTINE LOAD FOR (db=qualifiedName '.')? name=identifier
    ;

pauseRoutineLoadStatement
    : PAUSE ROUTINE LOAD FOR (db=qualifiedName '.')? name=identifier
    ;

showRoutineLoadStatement
    : SHOW ALL? ROUTINE LOAD (FOR (db=qualifiedName '.')? name=identifier)?
        (FROM db=qualifiedName)?
        (WHERE expression)? (ORDER BY sortItem (',' sortItem)*)? (limitElement)?
    ;

showRoutineLoadTaskStatement
    : SHOW ROUTINE LOAD TASK
        (FROM db=qualifiedName)?
        WHERE expression
    ;
// ------------------------------------------- Analyze Statement -------------------------------------------------------

analyzeStatement
    : ANALYZE (FULL | SAMPLE)? TABLE qualifiedName ('(' identifier (',' identifier)* ')')? properties?
    ;

dropStatsStatement
    : DROP STATS qualifiedName
    ;

analyzeHistogramStatement
    : ANALYZE TABLE qualifiedName UPDATE HISTOGRAM ON identifier (',' identifier)*
        (WITH bucket=INTEGER_VALUE BUCKETS)? properties?
    ;

dropHistogramStatement
    : ANALYZE TABLE qualifiedName DROP HISTOGRAM ON identifier (',' identifier)*
    ;

createAnalyzeStatement
    : CREATE ANALYZE (FULL | SAMPLE)? ALL properties?
    | CREATE ANALYZE (FULL | SAMPLE)? DATABASE db=identifier properties?
    | CREATE ANALYZE (FULL | SAMPLE)? TABLE qualifiedName ('(' identifier (',' identifier)* ')')? properties?
    ;

dropAnalyzeJobStatement
    : DROP ANALYZE INTEGER_VALUE
    ;

showAnalyzeStatement
    : SHOW ANALYZE (JOB | STATUS)? (WHERE expression)?
    ;

showStatsMetaStatement
    : SHOW STATS META (WHERE expression)?
    ;

showHistogramMetaStatement
    : SHOW HISTOGRAM META (WHERE expression)?
    ;

killAnalyzeStatement
    : KILL ANALYZE INTEGER_VALUE
    ;

// ------------------------------------------- Work Group Statement ----------------------------------------------------

createResourceGroupStatement
    : CREATE RESOURCE GROUP (IF NOT EXISTS)? (OR REPLACE)? identifier
        TO classifier (',' classifier)*  WITH '(' property (',' property)* ')'
    ;

dropResourceGroupStatement
    : DROP RESOURCE GROUP identifier
    ;

alterResourceGroupStatement
    : ALTER RESOURCE GROUP identifier ADD classifier (',' classifier)*
    | ALTER RESOURCE GROUP identifier DROP '(' INTEGER_VALUE (',' INTEGER_VALUE)* ')'
    | ALTER RESOURCE GROUP identifier DROP ALL
    | ALTER RESOURCE GROUP identifier WITH '(' property (',' property)* ')'
    ;

showResourceGroupStatement
    : SHOW RESOURCE GROUP identifier
    | SHOW RESOURCE GROUPS ALL?
    ;

createResourceStatement
    : CREATE EXTERNAL? RESOURCE resourceName=identifierOrString properties?
    ;

alterResourceStatement
    : ALTER RESOURCE resourceName=identifierOrString SET properties
    ;

dropResourceStatement
    : DROP RESOURCE resourceName=identifierOrString
    ;

showResourceStatement
    : SHOW RESOURCES
    ;

classifier
    : '(' expression (',' expression)* ')'
    ;

// ------------------------------------------- Function ----------------------------------------------------

showFunctionsStatement
    : SHOW FULL? BUILTIN? FUNCTIONS ((FROM | IN) db=qualifiedName)? ((LIKE pattern=string) | (WHERE expression))?
    ;

dropFunctionStatement
    : DROP FUNCTION qualifiedName '(' typeList ')'
    ;

createFunctionStatement
    : CREATE functionType=(TABLE | AGGREGATE)? FUNCTION qualifiedName '(' typeList ')' RETURNS returnType=type (INTERMEDIATE intermediateType =  type)? properties?
    ;

typeList
    : type?  ( ',' type)* (',' DOTDOTDOT) ?
    ;

// ------------------------------------------- Load Statement ----------------------------------------------------------

loadStatement
    : LOAD LABEL label=labelName
        data=dataDescList?
        broker=brokerDesc?
        (BY system=identifierOrString)?
        (PROPERTIES props=propertyList)?
    | LOAD LABEL label=labelName
        data=dataDescList?
        resource=resourceDesc
        (PROPERTIES props=propertyList)?
    ;

labelName
    : (db=identifier '.')? label=identifier
    ;

dataDescList
    : '(' dataDesc (',' dataDesc)* ')'
    ;

dataDesc
    : DATA INFILE srcFiles=stringList
        NEGATIVE?
        INTO TABLE dstTableName=identifier
        partitions=partitionNames?
        (COLUMNS TERMINATED BY colSep=string)?
        format=fileFormat?
        colList=columnAliases?
        (COLUMNS FROM PATH AS colFromPath=identifierList)?
        (SET colMappingList=classifier)?
        (WHERE where=expression)?
    | DATA FROM TABLE srcTableName=identifier
        NEGATIVE?
        INTO TABLE dstTableName=identifier
        partitions=partitionNames?
        (SET colMappingList=classifier)?
        (WHERE where=expression)?
    ;

brokerDesc
    : WITH BROKER props=propertyList?
    | WITH BROKER name=identifierOrString props=propertyList?
    ;

resourceDesc
    : WITH RESOURCE name=identifierOrString props=propertyList?
    ;

showLoadStatement
    : SHOW LOAD (FROM identifier)? (WHERE expression)? (ORDER BY sortItem (',' sortItem)*)? limitElement?
    ;

showLoadWarningsStatement
    : SHOW LOAD WARNINGS (FROM identifier)? (WHERE expression)? limitElement?
    | SHOW LOAD WARNINGS ON string
    ;

cancelLoadStatement
    : CANCEL LOAD (FROM identifier)? (WHERE expression)?
    ;

// ------------------------------------------- Show Statement ----------------------------------------------------------

showAuthorStatement
    : SHOW AUTHORS
    ;

showBackendsStatement
    : SHOW BACKENDS
    ;

showCharsetStatement
    : SHOW (CHAR SET | CHARSET) ((LIKE pattern=string) | (WHERE expression))?
    ;

showCollationStatement
    : SHOW COLLATION ((LIKE pattern=string) | (WHERE expression))?
    ;

showDeleteStatement
    : SHOW DELETE ((FROM | IN) db=qualifiedName)?
    ;

showDynamicPartitionStatement
    : SHOW DYNAMIC PARTITION TABLES ((FROM | IN) db=qualifiedName)?
    ;

showEventsStatement
    : SHOW EVENTS ((FROM | IN) catalog=qualifiedName)? ((LIKE pattern=string) | (WHERE expression))?
    ;

showEnginesStatement
    : SHOW ENGINES
    ;

showFrontendsStatement
    : SHOW FRONTENDS
    ;

showPluginsStatement
    : SHOW PLUGINS
    ;

showRepositoriesStatement
    : SHOW REPOSITORIES
    ;

showOpenTableStatement
    : SHOW OPEN TABLES
    ;

showProcedureStatement
    : SHOW PROCEDURE STATUS ((LIKE pattern=string) | (WHERE where=expression))?
    ;

showProcStatement
    : SHOW PROC path=string
    ;

showTriggersStatement
    : SHOW FULL? TRIGGERS ((FROM | IN) catalog=qualifiedName)? ((LIKE pattern=string) | (WHERE expression))?
    ;

showUserStatement
    : SHOW USER
    ;

showUserPropertyStatement
    : SHOW PROPERTY (FOR string)? (LIKE string)?
    ;

showVariablesStatement
    : SHOW varType? VARIABLES ((LIKE pattern=string) | (WHERE expression))?
    ;

showWarningStatement
    : SHOW (WARNINGS | ERRORS) (limitElement)?
    ;

// ------------------------------------------- Privilege Statement -----------------------------------------------------


privilegeObjectName
    : identifierOrString
    | tablePrivilegeObjectName
    | user
    ;

tablePrivilegeObjectName
    : identifierOrStringOrStar
    | identifierOrStringOrStar '.' identifierOrStringOrStar
    ;

identifierOrStringOrStar
    : ASTERISK_SYMBOL
    | identifier
    | string
    ;

privilegeActionReserved
    : ADMIN
    | ALTER
    | CREATE
    | DROP
    | GRANT
    | LOAD
    | SELECT
    ;

privilegeActionList
    :  privilegeAction (',' privilegeAction)*
    ;

privilegeAction
    : privilegeActionReserved
    | identifier
    ;

grantPrivilegeStatement
    : GRANT IMPERSONATE ON user TO ( user | ROLE identifierOrString )                                       #grantImpersonateBrief
    | GRANT privilegeActionList ON tablePrivilegeObjectName TO (user | ROLE identifierOrString)        #grantTablePrivBrief
    | GRANT privilegeActionList ON identifier privilegeObjectName TO (user | ROLE identifierOrString)  #grantPrivWithType
    ;

revokePrivilegeStatement
    : REVOKE IMPERSONATE ON user FROM ( user | ROLE identifierOrString )                                  #revokeImpersonateBrief
    | REVOKE privilegeActionList ON tablePrivilegeObjectName FROM (user | ROLE identifierOrString)   #revokeTablePrivBrief
    | REVOKE privilegeActionList ON identifier privilegeObjectName FROM (user | ROLE identifierOrString) #revokePrivWithType
    ;

grantRoleStatement
    : GRANT identifierOrString TO user
    ;

revokeRoleStatement
    : REVOKE identifierOrString FROM user
    ;

executeAsStatement
    : EXECUTE AS user (WITH NO REVERT)?
    ;

alterUserStatement
    : ALTER USER user authOption
    ;

createUserStatement
    : CREATE USER (IF NOT EXISTS)? user authOption? (DEFAULT ROLE string)?
    ;

dropUserStatement
    : DROP USER user
    ;

showAuthenticationStatement
    : SHOW ALL AUTHENTICATION                                                                #showAllAuthentication
    | SHOW AUTHENTICATION (FOR user)?                                                        #showAuthenticationForUser
    ;

createRoleStatement
    : CREATE ROLE identifierOrString                                                         #createRole
    ;

showRolesStatement
    : SHOW ROLES
    ;

showGrantsStatement
    : SHOW ALL? GRANTS (FOR user)?
    ;

dropRoleStatement
    : DROP ROLE identifierOrString                                                          #dropRole
    ;

// ------------------------------------------- Other Statement ---------------------------------------------------------

showDatabasesStatement
    : SHOW DATABASES ((FROM | IN) catalog=qualifiedName)? ((LIKE pattern=string) | (WHERE expression))?
    | SHOW SCHEMAS ((LIKE pattern=string) | (WHERE expression))?
    ;

showProcesslistStatement
    : SHOW FULL? PROCESSLIST
    ;

showStatusStatement
    : SHOW varType? STATUS ((LIKE pattern=string) | (WHERE expression))?
    ;

showTabletStatement
    : SHOW TABLET INTEGER_VALUE
    | SHOW TABLET FROM qualifiedName partitionNames? (WHERE expression)? (ORDER BY sortItem (',' sortItem)*)? (limitElement)?
    ;

killStatement
    : KILL (CONNECTION? | QUERY) INTEGER_VALUE
    ;

setUserPropertyStatement
    : SET PROPERTY (FOR string)? userPropertyList
    ;

showNodesStatement
    : SHOW COMPUTE NODES                                                       #showComputeNodes
    ;

showBrokerStatement
    : SHOW BROKER
    ;

setStatement
    : SET setVar (',' setVar)*
    ;

setVar
    : (CHAR SET | CHARSET) (identifierOrString | DEFAULT)                                       #setNames
    | NAMES (charset = identifierOrString | DEFAULT)
        (COLLATE (collate = identifierOrString | DEFAULT))?                                     #setNames
    | PASSWORD '=' (string | PASSWORD '(' string ')')                                           #setPassword
    | PASSWORD FOR user '=' (string | PASSWORD '(' string ')')                                  #setPassword
    | varType? identifier '=' setExprOrDefault                                                  #setVariable
    | userVariable '=' expression                                                               #setVariable
    | systemVariable '=' setExprOrDefault                                                       #setVariable
    ;

setExprOrDefault
    : DEFAULT
    | ON
    | ALL
    | expression
    ;

// ------------------------------------------- Query Statement ---------------------------------------------------------

queryStatement
    : explainDesc? queryBody outfile?;

queryBody
    : withClause? queryNoWith
    ;

withClause
    : WITH commonTableExpression (',' commonTableExpression)*
    ;

queryNoWith
    :queryTerm (ORDER BY sortItem (',' sortItem)*)? (limitElement)?
    ;

queryTerm
    : queryPrimary                                                             #queryTermDefault
    | left=queryTerm operator=INTERSECT setQuantifier? right=queryTerm         #setOperation
    | left=queryTerm operator=(UNION | EXCEPT | MINUS)
        setQuantifier? right=queryTerm                                         #setOperation
    ;

queryPrimary
    : querySpecification                           #queryPrimaryDefault
    | subquery                                     #subqueryPrimary
    ;

subquery
    : '(' queryBody  ')'
    ;

rowConstructor
     :'(' expression (',' expression)* ')'
     ;

sortItem
    : expression ordering = (ASC | DESC)? (NULLS nullOrdering=(FIRST | LAST))?
    ;

limitElement
    : LIMIT limit =INTEGER_VALUE (OFFSET offset=INTEGER_VALUE)?
    | LIMIT offset =INTEGER_VALUE ',' limit=INTEGER_VALUE
    ;

querySpecification
    : SELECT setVarHint* setQuantifier? selectItem (',' selectItem)*
      fromClause
      (WHERE where=expression)?
      (GROUP BY groupingElement)?
      (HAVING having=expression)?
    ;

fromClause
    : (FROM relations)?                                                                 #from
    | FROM DUAL                                                                         #dual
    ;

groupingElement
    : ROLLUP '(' (expression (',' expression)*)? ')'                                    #rollup
    | CUBE '(' (expression (',' expression)*)? ')'                                      #cube
    | GROUPING SETS '(' groupingSet (',' groupingSet)* ')'                              #multipleGroupingSets
    | expression (',' expression)*                                                      #singleGroupingSet
    ;

groupingSet
    : '(' expression? (',' expression)* ')'
    ;

commonTableExpression
    : name=identifier (columnAliases)? AS '(' queryBody ')'
    ;

setQuantifier
    : DISTINCT
    | ALL
    ;

selectItem
    : expression (AS? (identifier | string))?                                            #selectSingle
    | qualifiedName '.' ASTERISK_SYMBOL                                                  #selectAll
    | ASTERISK_SYMBOL                                                                    #selectAll
    ;

relations
    : relation (',' LATERAL? relation)*
    ;

relation
    : relationPrimary joinRelation*
    | '(' relationPrimary joinRelation* ')'
    ;

relationPrimary
    : qualifiedName partitionNames? tabletList? (
        AS? alias=identifier columnAliases?)? bracketHint?                              #tableAtom
    | '(' VALUES rowConstructor (',' rowConstructor)* ')'
        (AS? alias=identifier columnAliases?)?                                          #inlineTable
    | subquery (AS? alias=identifier columnAliases?)?                                   #subqueryRelation
    | qualifiedName '(' expression (',' expression)* ')'
        (AS? alias=identifier columnAliases?)?                                          #tableFunction
    | '(' relations ')'                                                                 #parenthesizedRelation
    ;

joinRelation
    : crossOrInnerJoinType bracketHint?
            LATERAL? rightRelation=relationPrimary joinCriteria?
    | outerAndSemiJoinType bracketHint?
            LATERAL? rightRelation=relationPrimary joinCriteria
    ;

crossOrInnerJoinType
    : JOIN | INNER JOIN
    | CROSS | CROSS JOIN
    ;

outerAndSemiJoinType
    : LEFT JOIN | RIGHT JOIN | FULL JOIN
    | LEFT OUTER JOIN | RIGHT OUTER JOIN
    | FULL OUTER JOIN
    | LEFT SEMI JOIN | RIGHT SEMI JOIN
    | LEFT ANTI JOIN | RIGHT ANTI JOIN
    ;

bracketHint
    : '[' identifier (',' identifier)* ']'
    ;

setVarHint
    : '/*+' SET_VAR '(' hintMap (',' hintMap)* ')' '*/'
    ;

hintMap
    : k=identifierOrString '=' v=literalExpression
    ;

joinCriteria
    : ON expression
    | USING '(' identifier (',' identifier)* ')'
    ;

columnAliases
    : '(' identifier (',' identifier)* ')'
    ;

partitionNames
    : TEMPORARY? (PARTITION | PARTITIONS) '(' identifier (',' identifier)* ')'
    | TEMPORARY? (PARTITION | PARTITIONS) identifier
    ;

tabletList
    : TABLET '(' INTEGER_VALUE (',' INTEGER_VALUE)* ')'
    ;

// ---------------------------------------- Backup Restore Statement -----------------------------------------------------
backupStatement
    : BACKUP SNAPSHOT qualifiedName
    TO identifier
    ON '(' tableDesc (',' tableDesc) * ')'
    (PROPERTIES propertyList)?
    ;

showBackupStatement
    : SHOW BACKUP ((FROM | IN) identifier)?
    ;

restoreStatement
    : RESTORE SNAPSHOT qualifiedName
    FROM identifier
    ON '(' restoreTableDesc (',' restoreTableDesc) * ')'
    (PROPERTIES propertyList)?
    ;

showRestoreStatement
    : SHOW RESTORE ((FROM | IN) identifier)? (WHERE where=expression)?
    ;

// ------------------------------------------- Snapshot Statement ------------------------------------------------------
showSnapshotStatement
    : SHOW SNAPSHOT ON identifier
    (WHERE expression)?
    ;

// ------------------------------------------- Expression --------------------------------------------------------------

/**
 * Operator precedences are shown in the following list, from highest precedence to the lowest.
 *
 * !
 * - (unary minus), ~ (unary bit inversion)
 * ^
 * *, /, DIV, %, MOD
 * -, +
 * &
 * |
 * = (comparison), <=>, >=, >, <=, <, <>, !=, IS, LIKE, REGEXP
 * BETWEEN, CASE WHEN
 * NOT
 * AND, &&
 * XOR
 * OR, ||
 * = (assignment)
 */

expressionsWithDefault
    : '(' expressionOrDefault (',' expressionOrDefault)* ')'
    ;

expressionOrDefault
    : expression | DEFAULT
    ;

expression
    : booleanExpression                                                                   #expressionDefault
    | NOT expression                                                                      #logicalNot
    | left=expression operator=(AND|LOGICAL_AND) right=expression                         #logicalBinary
    | left=expression operator=(OR|LOGICAL_OR) right=expression                           #logicalBinary
    ;

booleanExpression
    : predicate                                                                           #booleanExpressionDefault
    | booleanExpression IS NOT? NULL                                                      #isNull
    | left = booleanExpression comparisonOperator right = predicate                       #comparison
    | booleanExpression comparisonOperator '(' queryBody ')'                              #scalarSubquery
    ;

predicate
    : valueExpression (predicateOperations[$valueExpression.ctx])?
    ;

predicateOperations [ParserRuleContext value]
    : NOT? IN '(' expression (',' expression)* ')'                                        #inList
    | NOT? IN '(' queryBody ')'                                                           #inSubquery
    | NOT? BETWEEN lower = valueExpression AND upper = predicate                          #between
    | NOT? (LIKE | RLIKE | REGEXP) pattern=valueExpression                                #like
    ;

valueExpression
    : primaryExpression                                                                   #valueExpressionDefault
    | left = valueExpression operator = BITXOR right = valueExpression                    #arithmeticBinary
    | left = valueExpression operator = (
              ASTERISK_SYMBOL
            | SLASH_SYMBOL
            | PERCENT_SYMBOL
            | INT_DIV
            | MOD)
      right = valueExpression                                                             #arithmeticBinary
    | left = valueExpression operator = (PLUS_SYMBOL | MINUS_SYMBOL)
        right = valueExpression                                                           #arithmeticBinary
    | left = valueExpression operator = BITAND right = valueExpression                    #arithmeticBinary
    | left = valueExpression operator = BITOR right = valueExpression                     #arithmeticBinary
    ;

primaryExpression
    : userVariable                                                                        #userVariableExpression
    | systemVariable                                                                      #systemVariableExpression
    | columnReference                                                                     #columnRef
    | functionCall                                                                        #functionCallExpression
    | '{' FN functionCall '}'                                                             #odbcFunctionCallExpression
    | primaryExpression COLLATE (identifier | string)                                     #collate
    | literalExpression                                                                   #literal
    | left = primaryExpression CONCAT right = primaryExpression                           #concat
    | operator = (MINUS_SYMBOL | PLUS_SYMBOL | BITNOT) primaryExpression                  #arithmeticUnary
    | operator = LOGICAL_NOT primaryExpression                                            #arithmeticUnary
    | '(' expression ')'                                                                  #parenthesizedExpression
    | EXISTS '(' queryBody ')'                                                            #exists
    | subquery                                                                            #subqueryExpression
    | CAST '(' expression AS type ')'                                                     #cast
    | CASE caseExpr=expression whenClause+ (ELSE elseExpression=expression)? END          #simpleCase
    | CASE whenClause+ (ELSE elseExpression=expression)? END                              #searchedCase
    | arrayType? '[' (expression (',' expression)*)? ']'                                  #arrayConstructor
    | value=primaryExpression '[' index=valueExpression ']'                               #arraySubscript
    | primaryExpression '[' start=INTEGER_VALUE? ':' end=INTEGER_VALUE? ']'               #arraySlice
    | primaryExpression ARROW string                                                      #arrowExpression
    | (identifier | identifierList) '->' expression                                       #lambdaFunctionExpr
    ;

literalExpression
    : NULL                                                                                #nullLiteral
    | booleanValue                                                                        #booleanLiteral
    | number                                                                              #numericLiteral
    | (DATE | DATETIME) string                                                            #dateLiteral
    | string                                                                              #stringLiteral
    | interval                                                                            #intervalLiteral
    ;

functionCall
    : EXTRACT '(' identifier FROM valueExpression ')'                                     #extract
    | GROUPING '(' (expression (',' expression)*)? ')'                                    #groupingOperation
    | GROUPING_ID '(' (expression (',' expression)*)? ')'                                 #groupingOperation
    | informationFunctionExpression                                                       #informationFunction
    | specialFunctionExpression                                                           #specialFunction
    | aggregationFunction over?                                                           #aggregationFunctionCall
    | windowFunction over                                                                 #windowFunctionCall
    | qualifiedName '(' (expression (',' expression)*)? ')'  over?                        #simpleFunctionCall
    ;

aggregationFunction
    : AVG '(' DISTINCT? expression ')'
    | COUNT '(' ASTERISK_SYMBOL? ')'
    | COUNT '(' DISTINCT? (expression (',' expression)*)? ')'
    | MAX '(' DISTINCT? expression ')'
    | MIN '(' DISTINCT? expression ')'
    | SUM '(' DISTINCT? expression ')'
    ;

userVariable
    : AT identifierOrString
    ;

systemVariable
    : AT AT (varType '.')? identifier
    ;

columnReference
    : identifier
    | qualifiedName
    ;

informationFunctionExpression
    : name = DATABASE '(' ')'
    | name = SCHEMA '(' ')'
    | name = USER '(' ')'
    | name = CONNECTION_ID '(' ')'
    | name = CURRENT_USER '(' ')'
    ;

specialFunctionExpression
    : CHAR '(' expression ')'
    | CURRENT_TIMESTAMP '(' ')'
    | DAY '(' expression ')'
    | HOUR '(' expression ')'
    | IF '(' (expression (',' expression)*)? ')'
    | LEFT '(' expression ',' expression ')'
    | LIKE '(' expression ',' expression ')'
    | MINUTE '(' expression ')'
    | MOD '(' expression ',' expression ')'
    | MONTH '(' expression ')'
    | QUARTER '(' expression ')'
    | REGEXP '(' expression ',' expression ')'
    | REPLACE '(' (expression (',' expression)*)? ')'
    | RIGHT '(' expression ',' expression ')'
    | RLIKE '(' expression ',' expression ')'
    | SECOND '(' expression ')'
    | TIMESTAMPADD '(' unitIdentifier ',' expression ',' expression ')'
    | TIMESTAMPDIFF '(' unitIdentifier ',' expression ',' expression ')'
    //| WEEK '(' expression ')' TODO: Support week(expr) function
    | YEAR '(' expression ')'
    | PASSWORD '(' string ')'
    ;

windowFunction
    : name = ROW_NUMBER '(' ')'
    | name = RANK '(' ')'
    | name = DENSE_RANK '(' ')'
    | name = NTILE  '(' expression? ')'
    | name = LEAD  '(' (expression (',' expression)*)? ')'
    | name = LAG '(' (expression (',' expression)*)? ')'
    | name = FIRST_VALUE '(' (expression (',' expression)*)? ')'
    | name = LAST_VALUE '(' (expression (',' expression)*)? ')'
    ;

whenClause
    : WHEN condition=expression THEN result=expression
    ;

over
    : OVER '('
        (PARTITION BY partition+=expression (',' partition+=expression)*)?
        (ORDER BY sortItem (',' sortItem)*)?
        windowFrame?
      ')'
    ;

windowFrame
    : frameType=RANGE start=frameBound
    | frameType=ROWS start=frameBound
    | frameType=RANGE BETWEEN start=frameBound AND end=frameBound
    | frameType=ROWS BETWEEN start=frameBound AND end=frameBound
    ;

frameBound
    : UNBOUNDED boundType=PRECEDING                 #unboundedFrame
    | UNBOUNDED boundType=FOLLOWING                 #unboundedFrame
    | CURRENT ROW                                   #currentRowBound
    | expression boundType=(PRECEDING | FOLLOWING)  #boundedFrame
    ;

// ------------------------------------------- COMMON AST --------------------------------------------------------------

tableDesc
    : qualifiedName partitionNames?
    ;

restoreTableDesc
    : qualifiedName partitionNames? (AS identifier)?
    ;

explainDesc
    : (DESC | DESCRIBE | EXPLAIN) (LOGICAL | VERBOSE | COSTS)?
    ;

partitionDesc
    : PARTITION BY RANGE identifierList '(' (rangePartitionDesc (',' rangePartitionDesc)*)? ')'
    | PARTITION BY LIST identifierList '(' (listPartitionDesc (',' listPartitionDesc)*)? ')'
    ;

listPartitionDesc
    : singleItemListPartitionDesc
    | multiItemListPartitionDesc
    ;

singleItemListPartitionDesc
    : PARTITION (IF NOT EXISTS)? identifier VALUES IN stringList propertyList?
    ;

multiItemListPartitionDesc
    : PARTITION (IF NOT EXISTS)? identifier VALUES IN '(' stringList (',' stringList)* ')' propertyList?
    ;

stringList
    : '(' string (',' string)* ')'
    ;

rangePartitionDesc
    : singleRangePartition
    | multiRangePartition
    ;

singleRangePartition
    : PARTITION (IF NOT EXISTS)? identifier VALUES partitionKeyDesc propertyList?
    ;

multiRangePartition
    : START '(' string ')' END '(' string ')' EVERY '(' interval ')'
    | START '(' string ')' END '(' string ')' EVERY '(' INTEGER_VALUE ')'
    ;

partitionKeyDesc
    : LESS THAN (MAXVALUE | partitionValueList)
    | '[' partitionValueList ',' partitionValueList ')'
    ;

partitionValueList
    : '(' partitionValue (',' partitionValue)* ')'
    ;

partitionValue
    : MAXVALUE | string
    ;

distributionClause
    : DISTRIBUTED BY HASH identifierList (BUCKETS INTEGER_VALUE)?
    | DISTRIBUTED BY HASH identifierList
    ;

distributionDesc
    : DISTRIBUTED BY HASH identifierList (BUCKETS INTEGER_VALUE)?
    | DISTRIBUTED BY HASH identifierList
    ;

refreshSchemeDesc
    : REFRESH (ASYNC
    | ASYNC (START '(' string ')')? EVERY '(' interval ')'
    | MANUAL)
    ;

properties
    : PROPERTIES '(' property (',' property)* ')'
    ;

extProperties
    : BROKER properties
    ;

propertyList
    : '(' property (',' property)* ')'
    ;

userPropertyList
    : property (',' property)*
    ;

property
    : key=string '=' value=string
    ;

varType
    : GLOBAL
    | LOCAL
    | SESSION
    ;

comment
    : COMMENT string
    ;

columnNameWithComment
    : identifier comment?
    ;

outfile
    : INTO OUTFILE file=string fileFormat? properties?
    ;

fileFormat
    : FORMAT AS (identifier | string)
    ;

string
    : SINGLE_QUOTED_TEXT
    | DOUBLE_QUOTED_TEXT
    ;

comparisonOperator
    : EQ | NEQ | LT | LTE | GT | GTE | EQ_FOR_NULL
    ;

booleanValue
    : TRUE | FALSE
    ;

interval
    : INTERVAL value=expression from=unitIdentifier
    ;

unitIdentifier
    : YEAR | MONTH | WEEK | DAY | HOUR | MINUTE | SECOND | QUARTER
    ;

type
    : baseType
    | decimalType
    | arrayType
    ;

arrayType
    : ARRAY '<' type '>'
    ;

typeParameter
    : '(' INTEGER_VALUE ')'
    ;

baseType
    : BOOLEAN
    | TINYINT typeParameter?
    | SMALLINT typeParameter?
    | SIGNED INT?
    | INT typeParameter?
    | INTEGER typeParameter?
    | BIGINT typeParameter?
    | LARGEINT typeParameter?
    | FLOAT
    | DOUBLE
    | DATE
    | DATETIME
    | TIME
    | CHAR typeParameter?
    | VARCHAR typeParameter?
    | STRING
    | BITMAP
    | HLL
    | PERCENTILE
    | JSON
    ;

decimalType
    : (DECIMAL | DECIMALV2 | DECIMAL32 | DECIMAL64 | DECIMAL128) ('(' precision=INTEGER_VALUE (',' scale=INTEGER_VALUE)? ')')?
    ;

qualifiedName
    : identifier ('.' identifier)*
    ;

identifier
    : LETTER_IDENTIFIER      #unquotedIdentifier
    | nonReserved            #unquotedIdentifier
    | DIGIT_IDENTIFIER       #digitIdentifier
    | BACKQUOTED_IDENTIFIER  #backQuotedIdentifier
    ;

identifierList
    : '(' identifier (',' identifier)* ')'
    ;

identifierOrString
    : identifier
    | string
    ;

user
    : identifierOrString                                     # userWithoutHost
    | identifierOrString '@' identifierOrString              # userWithHost
    | identifierOrString '@' '[' identifierOrString ']'      # userWithHostAndBlanket
    ;

assignment
    : identifier EQ expressionOrDefault
    ;

assignmentList
    : assignment (',' assignment)*
    ;

number
    : DECIMAL_VALUE  #decimalValue
    | DOUBLE_VALUE   #doubleValue
    | INTEGER_VALUE  #integerValue
    ;

authOption
    : IDENTIFIED BY PASSWORD? string                            # authWithoutPlugin
    | IDENTIFIED WITH identifierOrString ((BY | AS) string)?    # authWithPlugin
    ;

nonReserved
    : AFTER | AGGREGATE | ASYNC | AUTHORS | AVG | ADMIN
    | BACKEND | BACKENDS | BACKUP | BEGIN | BITMAP_UNION | BOOLEAN | BROKER | BUCKETS | BUILTIN
    | CAST | CATALOG | CATALOGS | CHAIN | CHARSET | CURRENT | COLLATION | COLUMNS | COMMENT | COMMIT | COMMITTED
    | COMPUTE | CONNECTION | CONNECTION_ID | CONSISTENT | COSTS | COUNT | CONFIG
    | DATA | DATE | DATETIME | DAY | DECOMMISSION | DISTRIBUTION | DUPLICATE | DYNAMIC
    | END | ENGINE | ENGINES | ERRORS | EVENTS | EXECUTE | EXTERNAL | EXTRACT | EVERY
    | FILE | FILTER | FIRST | FOLLOWING | FORMAT | FN | FRONTEND | FRONTENDS | FOLLOWER | FREE | FUNCTIONS
    | GLOBAL | GRANTS
    | HASH | HISTOGRAM | HELP | HLL_UNION | HOUR | HUB
    | IDENTIFIED | IMPERSONATE | INDEXES | INSTALL | INTERMEDIATE | INTERVAL | ISOLATION
    | JOB
    | LABEL | LAST | LESS | LEVEL | LIST | LOCAL | LOGICAL
    | MANUAL | MATERIALIZED | MAX | META | MIN | MINUTE | MODIFY | MONTH | MERGE
    | NAME | NAMES | NEGATIVE | NO | NODE | NULLS
    | OBSERVER | OFFSET | ONLY | OPEN | OVERWRITE
    | PARTITIONS | PASSWORD | PATH | PAUSE | PERCENTILE_UNION | PLUGIN | PLUGINS | PRECEDING | PROC | PROCESSLIST
    | PROPERTIES | PROPERTY
    | QUARTER | QUERY | QUOTA
    | RANDOM | RECOVER | REFRESH | REPAIR | REPEATABLE | REPLACE_IF_NOT_NULL | REPLICA | REPOSITORY | REPOSITORIES
    | RESOURCE | RESOURCES | RESTORE | RESUME | RETURNS | REVERT | ROLE | ROLES | ROLLUP | ROLLBACK | ROUTINE
    | SAMPLE | SECOND | SERIALIZABLE | SESSION | SETS | SIGNED | SNAPSHOT | START | SUM | STATUS | STOP | STORAGE
    | STRING | STATS | SUBMIT | SYNC
    | TABLES | TABLET | TASK | TEMPORARY | TIMESTAMP | TIMESTAMPADD | TIMESTAMPDIFF | THAN | TIME | TRANSACTION
    | TRIGGERS | TRUNCATE | TYPE | TYPES
    | UNBOUNDED | UNCOMMITTED | UNINSTALL | USER
    | VALUE | VARIABLES | VIEW | VERBOSE
    | WARNINGS | WEEK | WORK | WRITE
    | YEAR
    | DOTDOTDOT
    ;
