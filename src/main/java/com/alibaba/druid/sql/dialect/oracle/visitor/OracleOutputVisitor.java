/*
 * Copyright 1999-2017 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.sql.dialect.oracle.visitor;

import java.util.List;

import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource.JoinType;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeIntervalDay;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeIntervalYear;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeTimestamp;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.CycleClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.CellAssignment;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.CellAssignmentItem;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.CellReferenceOption;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.MainModelClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.ModelColumn;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.ModelColumnClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.ModelRuleOption;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.ModelRulesClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.QueryPartitionClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.ReferenceModelClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.ModelClause.ReturnRowsClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.OracleLobStorageClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.OracleReturningClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.OracleStorageClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.OracleWithSubqueryEntry;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.PartitionExtensionClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.SampleClause;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.SearchClause;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleAnalytic;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleAnalyticWindowing;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleArgumentExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleBinaryDoubleExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleBinaryFloatExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleCursorExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleDatetimeExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleDbLinkExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleIntervalExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleIsSetExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleOuterExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleRangeExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleSizeExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.OracleSysdateExpr;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.*;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleAlterIndexStatement.Rebuild;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleAlterTableSplitPartition.NestedTablePartitionSpec;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleAlterTableSplitPartition.TableSpaceItem;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleAlterTableSplitPartition.UpdateIndexesClause;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleMultiInsertStatement.ConditionalInsertClause;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleMultiInsertStatement.ConditionalInsertClauseItem;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleMultiInsertStatement.InsertIntoClause;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleSelectPivot.Item;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleSelectRestriction.CheckOption;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleSelectRestriction.ReadOnly;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.util.JdbcConstants;

public class OracleOutputVisitor extends SQLASTOutputVisitor implements OracleASTVisitor {

    private final boolean printPostSemi;
    {
        this.dbType = JdbcConstants.ORACLE;
    }

    public OracleOutputVisitor(Appendable appender){
        this(appender, true);
    }

    public OracleOutputVisitor(Appendable appender, boolean printPostSemi){
        super(appender);
        this.printPostSemi = printPostSemi;
    }

    public boolean isPrintPostSemi() {
        return printPostSemi;
    }

    private void printHints(List<SQLHint> hints) {
        if (hints.size() > 0) {
            print0("/*+ ");
            printAndAccept(hints, ", ");
            print0(" */");
        }
    }

    public boolean visit(SQLAllColumnExpr x) {
        print('*');
        return false;
    }

    public boolean visit(OracleAnalytic x) {
        print0(ucase ? "OVER (" : "over (");
        
        boolean space = false;
        if (x.getPartitionBy().size() > 0) {
            print0(ucase ? "PARTITION BY " : "partition by ");
            printAndAccept(x.getPartitionBy(), ", ");

            space = true;
        }

        if (x.getOrderBy() != null) {
            if (space) {
                print(' ');
            }
            x.getOrderBy().accept(this);
            space = true;
        }

        if (x.getWindowing() != null) {
            if (space) {
                print(' ');
            }
            x.getWindowing().accept(this);
        }

        print(')');
        
        return false;
    }

    public boolean visit(OracleAnalyticWindowing x) {
        print0(x.getType().name().toUpperCase());
        print(' ');
        x.getExpr().accept(this);
        return false;
    }

    public boolean visit(OracleDbLinkExpr x) {
        SQLExpr expr = x.getExpr();
        if (expr != null) {
            expr.accept(this);
            print('@');
        }
        print0(x.getDbLink());
        return false;
    }

    public boolean visit(OracleDeleteStatement x) {
        print0(ucase ? "DELETE " : "delete ");

        SQLTableSource tableSource = x.getTableSource();
        if (x.getHints().size() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        print0(ucase ? "FROM " : "from ");
        if (x.isOnly()) {
            print0(ucase ? "ONLY (" : "only (");
            x.getTableName().accept(this);
            print(')');

            printAlias(x.getAlias());
        } else {
            x.getTableSource().accept(this);
        }

        if (x.getWhere() != null) {
            println();
            incrementIndent();
            print0(ucase ? "WHERE " : "where ");
            x.getWhere().setParent(x);
            x.getWhere().accept(this);
            decrementIndent();
        }

        if (x.getReturning() != null) {
            println();
            x.getReturning().accept(this);
        }

        return false;
    }

    public boolean visit(OracleIntervalExpr x) {
        if (x.getValue() instanceof SQLLiteralExpr) {
            print0(ucase ? "INTERVAL " : "interval ");
            x.getValue().accept(this);
            print(' ');
        } else {
            print('(');
            x.getValue().accept(this);
            print0(") ");
        }

        print0(x.getType().name());

        if (x.getPrecision() != null) {
            print('(');
            print(x.getPrecision().intValue());
            if (x.getFactionalSecondsPrecision() != null) {
                print0(", ");
                print(x.getFactionalSecondsPrecision().intValue());
            }
            print(')');
        }

        if (x.getToType() != null) {
            print0(ucase ? " TO " : " to ");
            print0(x.getToType().name());
            if (x.getToFactionalSecondsPrecision() != null) {
                print('(');
                print(x.getToFactionalSecondsPrecision().intValue());
                print(')');
            }
        }

        return false;
    }

    public boolean visit(OracleOuterExpr x) {
        x.getExpr().accept(this);
        print0("(+)");
        return false;
    }

    public boolean visit(OraclePLSQLCommitStatement astNode) {
        print('/');
        println();
        return false;
    }

    public boolean visit(SQLSelect x) {
        SQLWithSubqueryClause with = x.getWithSubQuery();
        if (with != null) {
            with.accept(this);
            println();
        }

        SQLSelectQuery query = x.getQuery();
        query.accept(this);

        if (x.getRestriction() != null) {
            print(' ');
            x.getRestriction().accept(this);
        }

        SQLOrderBy orderBy = x.getOrderBy();
        if (orderBy != null) {
            boolean hasFirst = false;
            if (query instanceof SQLSelectQueryBlock) {
                SQLSelectQueryBlock queryBlock = (SQLSelectQueryBlock) query;
                hasFirst = queryBlock.getFirst() != null;
            }

            if (!hasFirst) {
                println();
                orderBy.accept(this);
            }
        }

        return false;
    }

    public boolean visit(OracleSelectJoin x) {
        x.getLeft().accept(this);

        if (x.getJoinType() == JoinType.COMMA) {
            print0(", ");
            x.getRight().accept(this);
        } else {
            boolean isRoot = x.getParent() instanceof SQLSelectQueryBlock;
            if (isRoot) {
                incrementIndent();
            }

            println();
            print0(ucase ? x.getJoinType().name : x.getJoinType().name_lcase);
            print(' ');

            x.getRight().accept(this);

            if (isRoot) {
                decrementIndent();
            }

            if (x.getCondition() != null) {
                print0(ucase ? " ON " : " on ");
                x.getCondition().accept(this);
                print(' ');
            }

            if (x.getUsing().size() > 0) {
                print0(ucase ? " USING (" : " using (");
                printAndAccept(x.getUsing(), ", ");
                print(')');
            }

            printFlashback(x.getFlashback());
        }

        return false;
    }

    public boolean visit(SQLSelectOrderByItem x) {
        x.getExpr().accept(this);
        if (x.getType() != null) {
            print(' ');

            String typeName = x.getType().name();
            print0(ucase ? typeName.toUpperCase() : typeName.toLowerCase());
        }

        if (x.getNullsOrderType() != null) {
            print(' ');
            print0(x.getNullsOrderType().toFormalString());
        }

        return false;
    }

    public boolean visit(OracleSelectPivot x) {
        print0(ucase ? "PIVOT" : "pivot");
        if (x.isXml()) {
            print0(ucase ? " XML" : " xml");
        }
        print0(" (");
        printAndAccept(x.getItems(), ", ");

        if (x.getPivotFor().size() > 0) {
            print0(ucase ? " FOR " : " for ");
            if (x.getPivotFor().size() == 1) {
                ((SQLExpr) x.getPivotFor().get(0)).accept(this);
            } else {
                print('(');
                printAndAccept(x.getPivotFor(), ", ");
                print(')');
            }
        }

        if (x.getPivotIn().size() > 0) {
            print0(ucase ? " IN (" : " in (");
            printAndAccept(x.getPivotIn(), ", ");
            print(')');
        }

        print(')');

        return false;
    }

    public boolean visit(OracleSelectPivot.Item x) {
        x.getExpr().accept(this);
        if ((x.getAlias() != null) && (x.getAlias().length() > 0)) {
            print0(ucase ? " AS " : " as ");
            print0(x.getAlias());
        }
        return false;
    }

    public boolean visit(SQLSelectQueryBlock select) {
        if (select instanceof OracleSelectQueryBlock) {
            return visit((OracleSelectQueryBlock) select);
        }

        return super.visit(select);
    }

    public boolean visit(OracleSelectQueryBlock x) {
        if (isPrettyFormat() && x.hasBeforeComment()) {
            printlnComments(x.getBeforeCommentsDirect());
        }

        print0(ucase ? "SELECT " : "select ");

        if (x.getHintsSize() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        if (SQLSetQuantifier.ALL == x.getDistionOption()) {
            print0(ucase ? "ALL " : "all ");
        } else if (SQLSetQuantifier.DISTINCT == x.getDistionOption()) {
            print0(ucase ? "DISTINCT " : "distinct ");
        } else if (SQLSetQuantifier.UNIQUE == x.getDistionOption()) {
            print0(ucase ? "UNIQUE " : "unique ");
        }

        printSelectList(x.getSelectList());

        if (x.getInto() != null) {
            println();
            print0(ucase ? "INTO " : "into ");
            x.getInto().accept(this);
        }

        println();
        print0(ucase ? "FROM " : "from ");
        if (x.getFrom() == null) {
            print0(ucase ? "DUAL" : "dual");
        } else {
            x.getFrom().setParent(x);
            x.getFrom().accept(this);
        }

        if (x.getWhere() != null) {
            println();
            print0(ucase ? "WHERE " : "where ");
            x.getWhere().setParent(x);
            x.getWhere().accept(this);
        }

        printHierarchical(x);

        if (x.getGroupBy() != null) {
            println();
            x.getGroupBy().accept(this);
        }

        if (x.getModelClause() != null) {
            println();
            x.getModelClause().accept(this);
        }

        printFetchFirst(x);

        if (x.isForUpdate()) {
            println();
            print0(ucase ? "FOR UPDATE" : "for update");
            if (x.getForUpdateOfSize() > 0) {
                print('(');
                printAndAccept(x.getForUpdateOf(), ", ");
                print(')');
            }

            if (x.isNoWait()) {
                print0(ucase ? " NOWAIT" : " nowait");
            } else if (x.isSkipLocked()) {
                print0(ucase ? " SKIP LOCKED" : " skip locked");
            } else if (x.getWaitTime() != null) {
                print0(ucase ? " WAIT " : " wait ");
                x.getWaitTime().accept(this);
            }
        }

        return false;
    }

    public boolean visit(OracleSelectRestriction.CheckOption x) {
        print0(ucase ? "CHECK OPTION" : "check option");
        if (x.getConstraint() != null) {
            print(' ');
            x.getConstraint().accept(this);
        }
        return false;
    }

    public boolean visit(OracleSelectRestriction.ReadOnly x) {
        print0(ucase ? "READ ONLY" : "read only");
        return false;
    }

    public boolean visit(OracleSelectSubqueryTableSource x) {
        print('(');
        incrementIndent();
        println();
        x.getSelect().accept(this);
        decrementIndent();
        println();
        print(')');

        if (x.getPivot() != null) {
            println();
            x.getPivot().accept(this);
        }

        printFlashback(x.getFlashback());

        if ((x.getAlias() != null) && (x.getAlias().length() != 0)) {
            print(' ');
            print0(x.getAlias());
        }

        return false;
    }

    public boolean visit(OracleSelectTableReference x) {
        if (x.isOnly()) {
            print0(ucase ? "ONLY (" : "only (");
            printTableSourceExpr(x.getExpr());

            if (x.getPartition() != null) {
                print(' ');
                x.getPartition().accept(this);
            }

            print(')');
        } else {
            printTableSourceExpr(x.getExpr());

            if (x.getPartition() != null) {
                print(' ');
                x.getPartition().accept(this);
            }
        }

        if (x.getHints().size() > 0) {
            this.printHints(x.getHints());
        }

        if (x.getSampleClause() != null) {
            print(' ');
            x.getSampleClause().accept(this);
        }

        if (x.getPivot() != null) {
            println();
            x.getPivot().accept(this);
        }

        printFlashback(x.getFlashback());

        printAlias(x.getAlias());

        return false;
    }

    private void printFlashback(SQLExpr flashback) {
        if (flashback == null) {
            return;
        }

        println();

        if (flashback instanceof SQLBetweenExpr) {
            flashback.accept(this);
        } else {
            print0(ucase ? "AS OF " : "as of ");
            flashback.accept(this);
        }
    }

    public boolean visit(OracleSelectUnPivot x) {
        print0(ucase ? "UNPIVOT" : "unpivot");
        if (x.getNullsIncludeType() != null) {
            print(' ');
            print0(OracleSelectUnPivot.NullsIncludeType.toString(x.getNullsIncludeType(), ucase));
        }

        print0(" (");
        if (x.getItems().size() == 1) {
            ((SQLExpr) x.getItems().get(0)).accept(this);
        } else {
            print0(" (");
            printAndAccept(x.getItems(), ", ");
            print(')');
        }

        if (x.getPivotFor().size() > 0) {
            print0(ucase ? " FOR " : " for ");
            if (x.getPivotFor().size() == 1) {
                ((SQLExpr) x.getPivotFor().get(0)).accept(this);
            } else {
                print('(');
                printAndAccept(x.getPivotFor(), ", ");
                print(')');
            }
        }

        if (x.getPivotIn().size() > 0) {
            print0(ucase ? " IN (" : " in (");
            printAndAccept(x.getPivotIn(), ", ");
            print(')');
        }

        print(')');
        return false;
    }

    public boolean visit(OracleUpdateStatement x) {
        print0(ucase ? "UPDATE " : "update ");
        
        if (x.getHints().size() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        if (x.isOnly()) {
            print0(ucase ? "ONLY (" : "only (");
            x.getTableSource().accept(this);
            print(')');
        } else {
            x.getTableSource().accept(this);
        }

        printAlias(x.getAlias());

        println();

        print0(ucase ? "SET " : "set ");
        for (int i = 0, size = x.getItems().size(); i < size; ++i) {
            if (i != 0) {
                print0(", ");
            }
            x.getItems().get(i).accept(this);
        }

        if (x.getWhere() != null) {
            println();
            print0(ucase ? "WHERE " : "where ");
            incrementIndent();
            x.getWhere().setParent(x);
            x.getWhere().accept(this);
            decrementIndent();
        }

        if (x.getReturning().size() > 0) {
            println();
            print0(ucase ? "RETURNING " : "returning ");
            printAndAccept(x.getReturning(), ", ");
            print0(ucase ? " INTO " : " into ");
            printAndAccept(x.getReturningInto(), ", ");
        }

        return false;
    }

    @Override
    public void endVisit(OraclePLSQLCommitStatement astNode) {

    }

    @Override
    public void endVisit(OracleAnalytic x) {

    }

    @Override
    public void endVisit(OracleAnalyticWindowing x) {

    }

    @Override
    public void endVisit(OracleDbLinkExpr x) {

    }

    @Override
    public void endVisit(OracleDeleteStatement x) {

    }

    @Override
    public void endVisit(OracleIntervalExpr x) {

    }

    @Override
    public void endVisit(SQLMethodInvokeExpr x) {

    }

    @Override
    public void endVisit(OracleOuterExpr x) {

    }

    @Override
    public void endVisit(OracleSelectJoin x) {

    }

    @Override
    public void endVisit(OracleSelectPivot x) {

    }

    @Override
    public void endVisit(Item x) {

    }

    @Override
    public void endVisit(CheckOption x) {

    }

    @Override
    public void endVisit(ReadOnly x) {

    }

    @Override
    public void endVisit(OracleSelectSubqueryTableSource x) {

    }

    @Override
    public void endVisit(OracleSelectUnPivot x) {

    }


    @Override
    public void endVisit(OracleUpdateStatement x) {

    }

    @Override
    public boolean visit(SampleClause x) {
        print0(ucase ? "SAMPLE " : "sample ");

        if (x.isBlock()) {
            print0(ucase ? "BLOCK " : "block ");
        }

        print('(');
        printAndAccept(x.getPercent(), ", ");
        print(')');

        if (x.getSeedValue() != null) {
            print0(ucase ? " SEED (" : " seed (");
            x.getSeedValue().accept(this);
            print(')');
        }

        return false;
    }

    @Override
    public void endVisit(SampleClause x) {

    }

    @Override
    public void endVisit(OracleSelectTableReference x) {

    }

    @Override
    public boolean visit(PartitionExtensionClause x) {
        if (x.isSubPartition()) {
            print0(ucase ? "SUBPARTITION " : "subpartition ");
        } else {
            print0(ucase ? "PARTITION " : "partition ");
        }

        if (x.getPartition() != null) {
            print('(');
            x.getPartition().accept(this);
            print(')');
        } else {
            print0(ucase ? "FOR (" : "for (");
            printAndAccept(x.getFor(), ",");
            print(')');
        }
        return false;
    }

    @Override
    public void endVisit(PartitionExtensionClause x) {

    }

//    @Override
//    public boolean visit(VersionsFlashbackQueryClause x) {
//        print0(ucase ? "VERSIONS BETWEEN " : "versions between ");
//        print0(x.getType().name());
//        print(' ');
//        x.getBegin().accept(this);
//        print0(ucase ? " AND " : " and ");
//        x.getEnd().accept(this);
//        return false;
//    }
//
//    @Override
//    public void endVisit(VersionsFlashbackQueryClause x) {
//
//    }
//
//    @Override
//    public boolean visit(AsOfFlashbackQueryClause x) {
//        print0(ucase ? "AS OF " : "as of ");
//        print0(x.getType().name());
//        print0(" (");
//        x.getExpr().accept(this);
//        print(')');
//        return false;
//    }
//
//    @Override
//    public void endVisit(AsOfFlashbackQueryClause x) {
//
//    }

    @Override
    public boolean visit(OracleWithSubqueryEntry x) {
        x.getName().accept(this);

        if (x.getColumns().size() > 0) {
            print0(" (");
            printAndAccept(x.getColumns(), ", ");
            print(')');
        }

        print0(ucase ? " AS " : " as ");
        print('(');
        incrementIndent();
        println();
        x.getSubQuery().accept(this);
        decrementIndent();
        println();
        print(')');

        if (x.getSearchClause() != null) {
            println();
            x.getSearchClause().accept(this);
        }

        if (x.getCycleClause() != null) {
            println();
            x.getCycleClause().accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(OracleWithSubqueryEntry x) {

    }

    @Override
    public boolean visit(SearchClause x) {
        print0(ucase ? "SEARCH " : "search ");
        print0(x.getType().name());
        print0(ucase ? " FIRST BY " : " first by ");
        printAndAccept(x.getItems(), ", ");
        print0(ucase ? " SET " : " set ");
        x.getOrderingColumn().accept(this);

        return false;
    }

    @Override
    public void endVisit(SearchClause x) {

    }

    @Override
    public boolean visit(CycleClause x) {
        print0(ucase ? "CYCLE " : "cycle ");
        printAndAccept(x.getAliases(), ", ");
        print0(ucase ? " SET " : " set ");
        x.getMark().accept(this);
        print0(ucase ? " TO " : " to ");
        x.getValue().accept(this);
        print0(ucase ? " DEFAULT " : " default ");
        x.getDefaultValue().accept(this);

        return false;
    }

    @Override
    public void endVisit(CycleClause x) {

    }

    @Override
    public boolean visit(OracleBinaryFloatExpr x) {
        print0(x.getValue().toString());
        print('F');
        return false;
    }

    @Override
    public void endVisit(OracleBinaryFloatExpr x) {

    }

    @Override
    public boolean visit(OracleBinaryDoubleExpr x) {
        print0(x.getValue().toString());
        print('D');
        return false;
    }

    @Override
    public void endVisit(OracleBinaryDoubleExpr x) {

    }

    @Override
    public boolean visit(OracleCursorExpr x) {
        print0(ucase ? "CURSOR(" : "cursor(");
        incrementIndent();
        println();
        x.getQuery().accept(this);
        decrementIndent();
        println();
        print(')');
        return false;
    }

    @Override
    public void endVisit(OracleCursorExpr x) {

    }

    @Override
    public boolean visit(OracleIsSetExpr x) {
        x.getNestedTable().accept(this);
        print0(ucase ? " IS A SET" : " is a set");
        return false;
    }

    @Override
    public void endVisit(OracleIsSetExpr x) {

    }

    @Override
    public boolean visit(ReturnRowsClause x) {
        if (x.isAll()) {
            print0(ucase ? "RETURN ALL ROWS" : "return all rows");
        } else {
            print0(ucase ? "RETURN UPDATED ROWS" : "return updated rows");
        }
        return false;
    }

    @Override
    public void endVisit(ReturnRowsClause x) {

    }

    @Override
    public boolean visit(ModelClause x) {
        print0(ucase ? "MODEL" : "model");

        incrementIndent();
        for (CellReferenceOption opt : x.getCellReferenceOptions()) {
            print(' ');
            print0(opt.name);
        }

        if (x.getReturnRowsClause() != null) {
            print(' ');
            x.getReturnRowsClause().accept(this);
        }

        for (ReferenceModelClause item : x.getReferenceModelClauses()) {
            print(' ');
            item.accept(this);
        }

        x.getMainModel().accept(this);
        decrementIndent();

        return false;
    }

    @Override
    public void endVisit(ModelClause x) {

    }

    @Override
    public boolean visit(MainModelClause x) {
        if (x.getMainModelName() != null) {
            print0(ucase ? " MAIN " : " main ");
            x.getMainModelName().accept(this);
        }

        println();
        x.getModelColumnClause().accept(this);

        for (CellReferenceOption opt : x.getCellReferenceOptions()) {
            println();
            print0(opt.name);
        }

        println();
        x.getModelRulesClause().accept(this);

        return false;
    }

    @Override
    public void endVisit(MainModelClause x) {

    }

    @Override
    public boolean visit(ModelColumnClause x) {
        if (x.getQueryPartitionClause() != null) {
            x.getQueryPartitionClause().accept(this);
            println();
        }

        print0(ucase ? "DIMENSION BY (" : "dimension by (");
        printAndAccept(x.getDimensionByColumns(), ", ");
        print(')');

        println();
        print0(ucase ? "MEASURES (" : "measures (");
        printAndAccept(x.getMeasuresColumns(), ", ");
        print(')');
        return false;
    }

    @Override
    public void endVisit(ModelColumnClause x) {

    }

    @Override
    public boolean visit(QueryPartitionClause x) {
        print0(ucase ? "PARTITION BY (" : "partition by (");
        printAndAccept(x.getExprList(), ", ");
        print(')');
        return false;
    }

    @Override
    public void endVisit(QueryPartitionClause x) {

    }

    @Override
    public boolean visit(ModelColumn x) {
        x.getExpr().accept(this);
        if (x.getAlias() != null) {
            print(' ');
            print0(x.getAlias());
        }
        return false;
    }

    @Override
    public void endVisit(ModelColumn x) {

    }

    @Override
    public boolean visit(ModelRulesClause x) {
        if (x.getOptions().size() > 0) {
            print0(ucase ? "RULES" : "rules");
            for (ModelRuleOption opt : x.getOptions()) {
                print(' ');
                print0(opt.name);
            }
        }

        if (x.getIterate() != null) {
            print0(ucase ? " ITERATE (" : " iterate (");
            x.getIterate().accept(this);
            print(')');

            if (x.getUntil() != null) {
                print0(ucase ? " UNTIL (" : " until (");
                x.getUntil().accept(this);
                print(')');
            }
        }

        print0(" (");
        printAndAccept(x.getCellAssignmentItems(), ", ");
        print(')');
        return false;

    }

    @Override
    public void endVisit(ModelRulesClause x) {

    }

    @Override
    public boolean visit(CellAssignmentItem x) {
        if (x.getOption() != null) {
            print0(x.getOption().name);
            print(' ');
        }

        x.getCellAssignment().accept(this);

        if (x.getOrderBy() != null) {
            print(' ');
            x.getOrderBy().accept(this);
        }

        print0(" = ");
        x.getExpr().accept(this);

        return false;
    }

    @Override
    public void endVisit(CellAssignmentItem x) {

    }

    @Override
    public boolean visit(CellAssignment x) {
        x.getMeasureColumn().accept(this);
        print0("[");
        printAndAccept(x.getConditions(), ", ");
        print0("]");
        return false;
    }

    @Override
    public void endVisit(CellAssignment x) {

    }

    @Override
    public boolean visit(OracleReturningClause x) {
        print0(ucase ? "RETURNING " : "returning ");
        printAndAccept(x.getItems(), ", ");
        print0(ucase ? " INTO " : " into ");
        printAndAccept(x.getValues(), ", ");

        return false;
    }

    @Override
    public void endVisit(OracleReturningClause x) {

    }

    @Override
    public boolean visit(OracleInsertStatement x) {
        //visit((SQLInsertStatement) x);
        
        print0(ucase ? "INSERT " : "insert ");
        
        if (x.getHints().size() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        print0(ucase ? "INTO " : "into ");
        
        x.getTableSource().accept(this);

        printInsertColumns(x.getColumns());

        if (x.getValues() != null) {
            println();
            print0(ucase ? "VALUES " : "values ");
            x.getValues().accept(this);
        } else {
            if (x.getQuery() != null) {
                println();
                x.getQuery().setParent(x);
                x.getQuery().accept(this);
            }
        }

        if (x.getReturning() != null) {
            println();
            x.getReturning().accept(this);
        }

        if (x.getErrorLogging() != null) {
            println();
            x.getErrorLogging().accept(this);
        }

        return false;
    }

    @Override
    public void endVisit(OracleInsertStatement x) {
        endVisit((SQLInsertStatement) x);
    }

    @Override
    public boolean visit(InsertIntoClause x) {
        print0(ucase ? "INTO " : "into ");

        x.getTableSource().accept(this);

        if (x.getColumns().size() > 0) {
            incrementIndent();
            println();
            print('(');
            for (int i = 0, size = x.getColumns().size(); i < size; ++i) {
                if (i != 0) {
                    if (i % 5 == 0) {
                        println();
                    }
                    print0(", ");
                }
                x.getColumns().get(i).accept(this);
            }
            print(')');
            decrementIndent();
        }

        if (x.getValues() != null) {
            println();
            print0(ucase ? "VALUES " : "values ");
            x.getValues().accept(this);
        } else {
            if (x.getQuery() != null) {
                println();
                x.getQuery().setParent(x);
                x.getQuery().accept(this);
            }
        }

        return false;
    }

    @Override
    public void endVisit(InsertIntoClause x) {

    }

    @Override
    public boolean visit(OracleMultiInsertStatement x) {
        print0(ucase ? "INSERT " : "insert ");

        if (x.getHints().size() > 0) {
            this.printHints(x.getHints());
        }

        if (x.getOption() != null) {
            print0(x.getOption().name());
            print(' ');
        }

        for (int i = 0, size = x.getEntries().size(); i < size; ++i) {
            incrementIndent();
            println();
            x.getEntries().get(i).accept(this);
            decrementIndent();
        }

        println();
        x.getSubQuery().accept(this);

        return false;
    }

    @Override
    public void endVisit(OracleMultiInsertStatement x) {

    }

    @Override
    public boolean visit(ConditionalInsertClause x) {
        for (int i = 0, size = x.getItems().size(); i < size; ++i) {
            if (i != 0) {
                println();
            }

            ConditionalInsertClauseItem item = x.getItems().get(i);

            item.accept(this);
        }

        if (x.getElseItem() != null) {
            println();
            print0(ucase ? "ELSE" : "else");
            incrementIndent();
            println();
            x.getElseItem().accept(this);
            decrementIndent();
        }

        return false;
    }

    @Override
    public void endVisit(ConditionalInsertClause x) {

    }

    @Override
    public boolean visit(ConditionalInsertClauseItem x) {
        print0(ucase ? "WHEN " : "when ");
        x.getWhen().accept(this);
        print0(ucase ? " THEN" : " then");
        incrementIndent();
        println();
        x.getThen().accept(this);
        decrementIndent();
        return false;
    }

    @Override
    public void endVisit(ConditionalInsertClauseItem x) {

    }

    @Override
    public void endVisit(OracleSelectQueryBlock x) {

    }

    @Override
    public boolean visit(SQLBlockStatement x) {
        if (x.getParameters().size() != 0) {
            incrementIndent();
            if (x.getParent() instanceof SQLCreateProcedureStatement) {
                SQLCreateProcedureStatement procedureStatement = (SQLCreateProcedureStatement) x.getParent();
                if (procedureStatement.isCreate()) {
                    printIndent();
                }
            }
            if (!(x.getParent() instanceof SQLCreateProcedureStatement
                    || x.getParent() instanceof SQLCreateFunctionStatement)
                    ) {
                print0(ucase ? "DECLARE" : "declare");
                println();
            }

            for (int i = 0, size = x.getParameters().size(); i < size; ++i) {
                if (i != 0) {
                    println();
                }
                SQLParameter param = x.getParameters().get(i);
                param.accept(this);
                print(';');
            }

            decrementIndent();
            println();
        }
        print0(ucase ? "BEGIN" : "begin");
        incrementIndent();

        for (int i = 0, size = x.getStatementList().size(); i < size; ++i) {
            println();
            SQLStatement stmt = x.getStatementList().get(i);
            stmt.accept(this);
        }
        decrementIndent();

        SQLStatement exception = x.getException();
        if (exception != null) {
            println();
            exception.accept(this);
        }

        println();
        print0(ucase ? "END;" : "end;");
        return false;
    }

    @Override
    public void endVisit(SQLBlockStatement x) {

    }

    @Override
    public boolean visit(OracleLockTableStatement x) {
        print0(ucase ? "LOCK TABLE " : "lock table ");
        x.getTable().accept(this);
        print0(ucase ? " IN " : " in ");
        print0(x.getLockMode().toString());
        print0(ucase ? " MODE " : " mode ");
        if (x.isNoWait()) {
            print0(ucase ? "NOWAIT" : "nowait");
        } else if (x.getWait() != null) {
            print0(ucase ? "WAIT " : "wait ");
            x.getWait().accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(OracleLockTableStatement x) {

    }

    @Override
    public boolean visit(OracleAlterSessionStatement x) {
        print0(ucase ? "ALTER SESSION SET " : "alter session set ");
        printAndAccept(x.getItems(), ", ");
        return false;
    }

    @Override
    public void endVisit(OracleAlterSessionStatement x) {

    }

    @Override
    public boolean visit(OracleExprStatement x) {
        x.getExpr().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleExprStatement x) {

    }

    @Override
    public boolean visit(OracleDatetimeExpr x) {
        x.getExpr().accept(this);
        SQLExpr timeZone = x.getTimeZone();

        if (timeZone instanceof SQLIdentifierExpr) {
            if (((SQLIdentifierExpr) timeZone).getName().equalsIgnoreCase("LOCAL")) {
                print0(ucase ? " AT LOCAL" : "alter session set ");
                return false;
            }
        }

        print0(ucase ? " AT TIME ZONE " : " at time zone ");
        timeZone.accept(this);

        return false;
    }

    @Override
    public void endVisit(OracleDatetimeExpr x) {

    }

    @Override
    public boolean visit(OracleSysdateExpr x) {
        print0(ucase ? "SYSDATE" : "sysdate");
        if (x.getOption() != null) {
            print('@');
            print0(x.getOption());
        }
        return false;
    }

    @Override
    public void endVisit(OracleSysdateExpr x) {

    }

    @Override
    public void endVisit(com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleExceptionStatement.Item x) {

    }

    @Override
    public boolean visit(com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleExceptionStatement.Item x) {
        print0(ucase ? "WHEN " : "when ");
        x.getWhen().accept(this);
        print0(ucase ? " THEN" : " then");

        incrementIndent();
        if (x.getStatements().size() > 1) {
            println();
        } else {
            if (x.getStatements().size() == 1
                    && x.getStatements().get(0) instanceof SQLIfStatement) {
                println();
            } else {
                print(' ');
            }
        }

        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            if (i != 0 && size > 1) {
                println();
            }
            SQLStatement stmt = x.getStatements().get(i);
            stmt.accept(this);
        }

        decrementIndent();
        return false;
    }

    @Override
    public boolean visit(OracleExceptionStatement x) {
        print0(ucase ? "EXCEPTION" : "exception");
        incrementIndent();
        List<OracleExceptionStatement.Item> items = x.getItems();
        for (int i = 0, size = items.size(); i < size; ++i) {
            println();
            OracleExceptionStatement.Item item = items.get(i);
            item.accept(this);
        }
        decrementIndent();
        return false;
    }

    @Override
    public void endVisit(OracleExceptionStatement x) {

    }

    @Override
    public boolean visit(OracleArgumentExpr x) {
        print0(x.getArgumentName());
        print0(" => ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleArgumentExpr x) {

    }

    @Override
    public boolean visit(OracleSetTransactionStatement x) {
        if (x.isReadOnly()) {
            print0(ucase ? "SET TRANSACTION READ ONLY NAME " : "set transaction read only name ");
        } else {
            print0(ucase ? "SET TRANSACTION NAME " : "set transaction name ");
        }
        x.getName().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleSetTransactionStatement x) {

    }

    @Override
    public boolean visit(OracleExplainStatement x) {
        print0(ucase ? "EXPLAIN PLAN" : "explain plan");
        incrementIndent();
        println();
        if (x.getStatementId() != null) {
            print0(ucase ? "SET STATEMENT_ID = " : "set statement_id = ");
            x.getStatementId().accept(this);
            println();
        }

        if (x.getInto() != null) {
            print0(ucase ? "INTO " : "into ");
            x.getInto().accept(this);
            println();
        }

        print0(ucase ? "FRO" : "fro");
        println();
        x.getStatement().accept(this);

        decrementIndent();
        return false;
    }

    @Override
    public void endVisit(OracleExplainStatement x) {

    }

    @Override
    public boolean visit(OracleAlterProcedureStatement x) {
        print0(ucase ? "ALTER PROCEDURE " : "alter procedure ");
        x.getName().accept(this);
        if (x.isCompile()) {
            print0(ucase ? " COMPILE" : " compile");
        }
        if (x.isReuseSettings()) {
            print0(ucase ? " REUSE SETTINGS" : " reuse settings");
        }
        return false;
    }

    @Override
    public void endVisit(OracleAlterProcedureStatement x) {

    }

    @Override
    public boolean visit(OracleAlterTableDropPartition x) {
        print0(ucase ? "DROP PARTITION " : "drop partition ");
        x.getName().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleAlterTableDropPartition x) {

    }

    @Override
    public boolean visit(SQLAlterTableStatement x) {
        if (x.getItems().size() == 1) {
            SQLAlterTableItem item = x.getItems().get(0);
            if (item instanceof SQLAlterTableRename) {
                SQLExpr to = ((SQLAlterTableRename) item).getTo().getExpr();

                print0(ucase ? "RENAME " : "rename ");
                x.getName().accept(this);
                print0(ucase ? " TO " : "to ");
                to.accept(this);
                return false;
            }
        }

        print0(ucase ? "ALTER TABLE " : "alter table ");
        printTableSourceExpr(x.getName());
        incrementIndent();
        for (SQLAlterTableItem item : x.getItems()) {
            println();
            item.accept(this);
        }
        if (x.isUpdateGlobalIndexes()) {
            println();
            print0(ucase ? "UPDATE GLOABL INDEXES" : "update gloabl indexes");
        }
        decrementIndent();
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableTruncatePartition x) {
        print0(ucase ? "TRUNCATE PARTITION " : "truncate partition ");
        x.getName().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleAlterTableTruncatePartition x) {

    }

    @Override
    public boolean visit(TableSpaceItem x) {
        print0(ucase ? "TABLESPACE " : "tablespace ");
        x.getTablespace().accept(this);
        return false;
    }

    @Override
    public void endVisit(TableSpaceItem x) {

    }

    @Override
    public boolean visit(UpdateIndexesClause x) {
        print0(ucase ? "UPDATE INDEXES" : "update indexes");
        if (x.getItems().size() > 0) {
            print('(');
            printAndAccept(x.getItems(), ", ");
            print(')');
        }
        return false;
    }

    @Override
    public void endVisit(UpdateIndexesClause x) {

    }

    @Override
    public boolean visit(OracleAlterTableSplitPartition x) {
        print0(ucase ? "SPLIT PARTITION " : "split partition ");
        x.getName().accept(this);

        if (x.getAt().size() > 0) {
            incrementIndent();
            println();
            print0(ucase ? "AT (" : "at (");
            printAndAccept(x.getAt(), ", ");
            print(')');
            decrementIndent();
        }

        if (x.getInto().size() > 0) {
            println();
            incrementIndent();
            print0(ucase ? "INTO (" : "into (");
            printAndAccept(x.getInto(), ", ");
            print(')');
            decrementIndent();
        }

        if (x.getUpdateIndexes() != null) {
            println();
            incrementIndent();
            x.getUpdateIndexes().accept(this);
            decrementIndent();
        }
        return false;
    }

    @Override
    public void endVisit(OracleAlterTableSplitPartition x) {

    }

    @Override
    public boolean visit(NestedTablePartitionSpec x) {
        print0(ucase ? "PARTITION " : "partition ");
        x.getPartition().accept(this);
        for (SQLObject item : x.getSegmentAttributeItems()) {
            print(' ');
            item.accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(NestedTablePartitionSpec x) {

    }

    @Override
    public boolean visit(OracleAlterTableModify x) {
        print0(ucase ? "MODIFY (" : "modify (");
        incrementIndent();
        for (int i = 0, size = x.getColumns().size(); i < size; ++i) {
            println();
            SQLColumnDefinition column = x.getColumns().get(i);
            column.accept(this);
            if (i != size - 1) {
                print0(", ");
            }
        }
        decrementIndent();
        println();
        print(')');

        return false;
    }

    @Override
    public void endVisit(OracleAlterTableModify x) {

    }

    @Override
    public boolean visit(OracleCreateIndexStatement x) {
        print0(ucase ? "CREATE " : "create ");
        if (x.getType() != null) {
            print0(x.getType());
            print(' ');
        }

        print0(ucase ? "INDEX " : "index ");

        x.getName().accept(this);
        print0(ucase ? " ON " : " on ");
        x.getTable().accept(this);
        print('(');
        printAndAccept(x.getItems(), ", ");
        print(')');

        if (x.isIndexOnlyTopLevel()) {
            println();
            print0(ucase ? "INDEX ONLY TOPLEVEL" : "index only toplevel");
        }

        if (x.isComputeStatistics()) {
            println();
            print0(ucase ? "COMPUTE STATISTICS" : "compute statistics");
        }

        if (x.getTablespace() != null) {
            println();
            print0(ucase ? "TABLESPACE " : "tablespace ");
            x.getTablespace().accept(this);
        }

        if (x.isOnline()) {
            print0(ucase ? " ONLINE" : " online");
        }

        if (x.isNoParallel()) {
            print0(ucase ? " NOPARALLEL" : " noparallel");
        } else if (x.getParallel() != null) {
            print0(ucase ? " PARALLEL " : " parallel ");
            x.getParallel().accept(this);
        }

        this.printOracleSegmentAttributes(x);

        return false;
    }

    @Override
    public void endVisit(OracleCreateIndexStatement x) {

    }

    @Override
    public boolean visit(OracleAlterIndexStatement x) {
        print0(ucase ? "ALTER INDEX " : "alter index ");
        x.getName().accept(this);

        if (x.getRenameTo() != null) {
            print0(ucase ? " RENAME TO " : " rename to ");
            x.getRenameTo().accept(this);
        }

        if (x.getMonitoringUsage() != null) {
            print0(ucase ? " MONITORING USAGE" : " monitoring usage");
        }

        if (x.getRebuild() != null) {
            print(' ');
            x.getRebuild().accept(this);
        }

        if (x.getParallel() != null) {
            print0(ucase ? " PARALLEL" : " parallel");
            x.getParallel().accept(this);
        }

        return false;
    }

    @Override
    public void endVisit(OracleAlterIndexStatement x) {

    }

    @Override
    public boolean visit(Rebuild x) {
        print0(ucase ? "REBUILD" : "rebuild");

        if (x.getOption() != null) {
            print(' ');
            x.getOption().accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(Rebuild x) {

    }

    @Override
    public boolean visit(OracleForStatement x) {
        boolean all = x.isAll();
        if (all) {
            print0(ucase ? "FORALL " : "forall ");
        } else {
            print0(ucase ? "FOR " : "for ");
        }
        x.getIndex().accept(this);
        print0(ucase ? " IN " : " in ");
        x.getRange().accept(this);
        if (!all) {
            println();
            print0(ucase ? "LOOP" : "loop");
        }
        incrementIndent();
        println();

        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            SQLStatement stmt = x.getStatements().get(i);
            stmt.accept(this);
            if (!all) {
                if (i != size - 1) {
                    println();
                }
            }
        }

        decrementIndent();
        if (!all) {
            println();
            print0(ucase ? "END LOOP" : "end loop");
            SQLName endLabel = x.getEndLabel();
            if (endLabel != null) {
                print(' ');
                endLabel.accept(this);
            }
        }
        return false;
    }

    @Override
    public void endVisit(OracleForStatement x) {

    }

    @Override
    public boolean visit(SQLIfStatement.Else x) {
        print0(ucase ? "ELSE" : "else");
        incrementIndent();
        println();

        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            if (i != 0) {
                println();
            }
            SQLStatement item = x.getStatements().get(i);
            item.setParent(x);
            item.accept(this);
        }

        decrementIndent();
        return false;
    }

    @Override
    public boolean visit(SQLIfStatement.ElseIf x) {
        print0(ucase ? "ELSE IF " : "else if ");
        x.getCondition().accept(this);
        print0(ucase ? " THEN" : " then");
        incrementIndent();

        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            println();
            SQLStatement item = x.getStatements().get(i);
            item.accept(this);
        }

        decrementIndent();
        return false;
    }

    @Override
    public boolean visit(SQLIfStatement x) {
        print0(ucase ? "IF " : "if ");
        int lines = this.lines;
        incrementIndent();
        x.getCondition().accept(this);
        decrementIndent();

        if (lines != this.lines) {
            println();
        } else {
            print(' ');
        }
        print0(ucase ? "THEN" : "then");

        incrementIndent();
        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            println();
            SQLStatement item = x.getStatements().get(i);
            item.accept(this);
        }
        decrementIndent();

        for (SQLIfStatement.ElseIf elseIf : x.getElseIfList()) {
            println();
            elseIf.accept(this);
        }

        if (x.getElseItem() != null) {
            println();
            x.getElseItem().accept(this);
        }
        println();
        print0(ucase ? "END IF" : "end if");
        return false;
    }

    @Override
    public boolean visit(OracleRangeExpr x) {
        x.getLowBound().accept(this);
        print0("..");
        x.getUpBound().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleRangeExpr x) {

    }

    protected void visitColumnDefault(SQLColumnDefinition x) {
        if (x.getParent() instanceof SQLBlockStatement) {
            print0(" := ");
        } else {
            print0(ucase ? " DEFAULT " : " default ");
        }
        x.getDefaultExpr().accept(this);
    }

    @Override
    public boolean visit(OraclePrimaryKey x) {
        if (x.getName() != null) {
            print0(ucase ? "CONSTRAINT " : "constraint ");
            x.getName().accept(this);
            print(' ');
        }
        print0(ucase ? "PRIMARY KEY (" : "primary key (");
        printAndAccept(x.getColumns(), ", ");
        print(')');

        printConstraintState(x);

        return false;
    }

    protected void printConstraintState(OracleConstraint x) {
        incrementIndent();
        if (x.getUsing() != null) {
            println();
            x.getUsing().accept(this);
        }

        if (x.getExceptionsInto() != null) {
            println();
            print0(ucase ? "EXCEPTIONS INTO " : "exceptions into ");
            x.getExceptionsInto().accept(this);
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                print0(ucase ? " ENABLE" : " enable");
            } else {
                print0(ucase ? " DIABLE" : " diable");
            }
        }

        if (x.getInitially() != null) {
            print0(ucase ? " INITIALLY " : " initially ");
            print0(x.getInitially().name());
        }

        if (x.getDeferrable() != null) {
            if (x.getDeferrable().booleanValue()) {
                print0(ucase ? " DEFERRABLE" : " deferrable");
            } else {
                print0(ucase ? " NOT DEFERRABLE" : " not deferrable");
            }
        }
        decrementIndent();
    }

    @Override
    public void endVisit(OraclePrimaryKey x) {

    }

    @Override
    public boolean visit(OracleCreateTableStatement x) {
        printCreateTable(x);

        if (x.getOf() != null) {
            println();
            print0(ucase ? "OF " : "of ");
            x.getOf().accept(this);
        }

        if (x.getOidIndex() != null) {
            println();
            x.getOidIndex().accept(this);
        }

        if (x.getOrganization() != null) {
            println();
            incrementIndent();
            x.getOrganization().accept(this);
            decrementIndent();
        }

        printOracleSegmentAttributes(x);

        if (x.isInMemoryMetadata()) {
            println();
            print0(ucase ? "IN_MEMORY_METADATA" : "in_memory_metadata");
        }

        if (x.isCursorSpecificSegment()) {
            println();
            print0(ucase ? "CURSOR_SPECIFIC_SEGMENT" : "cursor_specific_segment");
        }

        if (x.getParallel() == Boolean.TRUE) {
            println();
            print0(ucase ? "PARALLEL" : "parallel");
        } else if (x.getParallel() == Boolean.FALSE) {
            println();
            print0(ucase ? "NOPARALLEL" : "noparallel");
        }

        if (x.getCache() == Boolean.TRUE) {
            println();
            print0(ucase ? "CACHE" : "cache");
        } else if (x.getCache() == Boolean.FALSE) {
            println();
            print0(ucase ? "NOCACHE" : "nocache");
        }

        if (x.getLobStorage() != null) {
            println();
            x.getLobStorage().accept(this);
        }

        if (x.isOnCommit()) {
            println();
            print0(ucase ? "ON COMMIT" : "on commit");
        }

        if (x.isPreserveRows()) {
            println();
            print0(ucase ? "PRESERVE ROWS" : "preserve rows");
        }

        if (x.getPartitioning() != null) {
            println();
            x.getPartitioning().accept(this);
        }

        if (x.getCluster() != null) {
            println();
            print0(ucase ? "CLUSTER " : "cluster ");
            x.getCluster().accept(this);
            print0(" (");
            printAndAccept(x.getClusterColumns(), ",");
            print0(")");
        }

        if (x.getSelect() != null) {
            println();
            print0(ucase ? "AS" : "as");
            println();
            x.getSelect().accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(OracleCreateTableStatement x) {

    }

    @Override
    public boolean visit(OracleStorageClause x) {
        print0(ucase ? "STORAGE (" : "storage (");

        incrementIndent();
        if (x.getInitial() != null) {
            println();
            print0(ucase ? "INITIAL " : "initial ");
            x.getInitial().accept(this);
        }

        if (x.getNext() != null) {
            println();
            print0(ucase ? "NEXT " : "next ");
            x.getNext().accept(this);
        }

        if (x.getMinExtents() != null) {
            println();
            print0(ucase ? "MINEXTENTS " : "minextents ");
            x.getMinExtents().accept(this);
        }

        if (x.getMaxExtents() != null) {
            println();
            print0(ucase ? "MAXEXTENTS " : "maxextents ");
            x.getMaxExtents().accept(this);
        }

        if (x.getPctIncrease() != null) {
            println();
            print0(ucase ? "PCTINCREASE " : "pctincrease ");
            x.getPctIncrease().accept(this);
        }

        if (x.getMaxSize() != null) {
            println();
            print0(ucase ? "MAXSIZE " : "maxsize ");
            x.getMaxSize().accept(this);
        }

        if (x.getFreeLists() != null) {
            println();
            print0(ucase ? "FREELISTS " : "freelists ");
            x.getFreeLists().accept(this);
        }

        if (x.getFreeListGroups() != null) {
            println();
            print0(ucase ? "FREELIST GROUPS " : "freelist groups ");
            x.getFreeListGroups().accept(this);
        }

        if (x.getBufferPool() != null) {
            println();
            print0(ucase ? "BUFFER_POOL " : "buffer_pool ");
            x.getBufferPool().accept(this);
        }

        if (x.getObjno() != null) {
            println();
            print0(ucase ? "OBJNO " : "objno ");
            x.getObjno().accept(this);
        }

        if (x.getFlashCache() != null) {
            println();
            print0(ucase ? "FLASH_CACHE " : "flash_cache ");
            print0(ucase ? x.getFlashCache().name() : x.getFlashCache().name().toLowerCase());
        }

        if (x.getCellFlashCache() != null) {
            println();
            print0(ucase ? "CELL_FLASH_CACHE " : "cell_flash_cache ");
            print0(ucase ? x.getCellFlashCache().name() : x.getCellFlashCache().name().toLowerCase());
        }
        decrementIndent();
        println();
        print(')');

        return false;
    }

    @Override
    public void endVisit(OracleStorageClause x) {

    }

    @Override
    public boolean visit(OracleGotoStatement x) {
        print0(ucase ? "GOTO " : "GOTO ");
        x.getLabel().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleGotoStatement x) {

    }

    @Override
    public boolean visit(OracleLabelStatement x) {
        print0("<<");
        x.getLabel().accept(this);
        print0(">>");
        return false;
    }

    @Override
    public void endVisit(OracleLabelStatement x) {

    }

    @Override
    public boolean visit(OracleAlterTriggerStatement x) {
        print0(ucase ? "ALTER TRIGGER " : "alter trigger ");
        x.getName().accept(this);

        if (x.isCompile()) {
            print0(ucase ? " COMPILE" : " compile");
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                print0(ucase ? "ENABLE" : "enable");
            } else {
                print0(ucase ? "DISABLE" : "disable");
            }
        }
        return false;
    }

    @Override
    public void endVisit(OracleAlterTriggerStatement x) {

    }

    @Override
    public boolean visit(OracleAlterSynonymStatement x) {
        print0(ucase ? "ALTER SYNONYM " : "alter synonym ");
        x.getName().accept(this);

        if (x.isCompile()) {
            print0(ucase ? " COMPILE" : " compile");
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                print0(ucase ? "ENABLE" : "enable");
            } else {
                print0(ucase ? "DISABLE" : "disable");
            }
        }
        return false;
    }

    @Override
    public void endVisit(OracleAlterSynonymStatement x) {

    }

//    @Override
//    public boolean visit(AsOfSnapshotClause x) {
//        print0(ucase ? "AS OF SNAPSHOT(" : "as of snapshot(");
//        x.getExpr().accept(this);
//        print(')');
//        return false;
//    }
//
//    @Override
//    public void endVisit(AsOfSnapshotClause x) {
//
//    }

    @Override
    public boolean visit(OracleAlterViewStatement x) {
        print0(ucase ? "ALTER VIEW " : "alter view ");
        x.getName().accept(this);

        if (x.isCompile()) {
            print0(ucase ? " COMPILE" : " compile");
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                print0(ucase ? "ENABLE" : "enable");
            } else {
                print0(ucase ? "DISABLE" : "disable");
            }
        }
        return false;
    }

    @Override
    public void endVisit(OracleAlterViewStatement x) {

    }

    @Override
    public boolean visit(OracleAlterTableMoveTablespace x) {
        print0(ucase ? " MOVE TABLESPACE " : " move tablespace ");
        x.getName().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleAlterTableMoveTablespace x) {

    }

    @Override
    public boolean visit(OracleSizeExpr x) {
        x.getValue().accept(this);
        print0(x.getUnit().name());
        return false;
    }

    @Override
    public void endVisit(OracleSizeExpr x) {

    }

    @Override
    public boolean visit(OracleFileSpecification x) {
        printAndAccept(x.getFileNames(), ", ");

        if (x.getSize() != null) {
            print0(ucase ? " SIZE " : " size ");
            x.getSize().accept(this);
        }

        if (x.isAutoExtendOff()) {
            print0(ucase ? " AUTOEXTEND OFF" : " autoextend off");
        } else if (x.getAutoExtendOn() != null) {
            print0(ucase ? " AUTOEXTEND ON " : " autoextend on ");
            x.getAutoExtendOn().accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(OracleFileSpecification x) {

    }

    @Override
    public boolean visit(OracleAlterTablespaceAddDataFile x) {
        print0(ucase ? "ADD DATAFILE" : "add datafile");
        incrementIndent();
        for (OracleFileSpecification file : x.getFiles()) {
            println();
            file.accept(this);
        }
        decrementIndent();
        return false;
    }

    @Override
    public void endVisit(OracleAlterTablespaceAddDataFile x) {

    }

    @Override
    public boolean visit(OracleAlterTablespaceStatement x) {
        print0(ucase ? "ALTER TABLESPACE " : "alter tablespace ");
        x.getName().accept(this);
        println();
        x.getItem().accept(this);
        return false;
    }

    @Override
    public void endVisit(OracleAlterTablespaceStatement x) {

    }

    @Override
    public boolean visit(SQLTruncateStatement x) {
        print0(ucase ? "TRUNCATE TABLE " : "truncate table ");
        printAndAccept(x.getTableSources(), ", ");

        if (x.isPurgeSnapshotLog()) {
            print0(ucase ? " PURGE SNAPSHOT LOG" : " purge snapshot log");
        }
        return false;
    }

    @Override
    public boolean visit(OracleExitStatement x) {
        print0(ucase ? "EXIT" : "exit");

        if (x.getLabel() != null) {
            print(' ');
            print0(x.getLabel());
        }

        if (x.getWhen() != null) {
            print0(ucase ? " WHEN " : " when ");
            x.getWhen().accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(OracleExitStatement x) {

    }


    @Override
    public boolean visit(OracleContinueStatement x) {
        print0(ucase ? "CONTINUE" : "continue");

        String label = x.getLabel();
        if (label != null) {
            print(' ');
            print0(label);
        }

        if (x.getWhen() != null) {
            print0(ucase ? " WHEN " : " when ");
            x.getWhen().accept(this);
        }
        return false;
    }

    @Override
    public void endVisit(OracleContinueStatement x) {

    }

    @Override
    public boolean visit(OracleRaiseStatement x) {
        print0(ucase ? "RAISE" : "raise");
        if (x.getException() != null) {
            print(' ');
            x.getException().accept(this);
        }
        print(';');
        return false;
    }

    @Override
    public void endVisit(OracleRaiseStatement x) {

    }

    @Override
    public void endVisit(SQLRollbackStatement x) {

    }

    @Override
    public boolean visit(SQLSavePointStatement x) {
        print0(ucase ? "SAVEPOINT" : "savepoint");
        if (x.getName() != null) {
            print0(ucase ? " TO " : " to ");
            x.getName().accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(SQLCreateProcedureStatement x) {
        boolean create = x.isCreate();
        if (!create) {
            print0(ucase ? "PROCEDURE " : "procedure ");
        } else if (x.isOrReplace()) {
            print0(ucase ? "CREATE OR REPLACE PROCEDURE " : "create or replace procedure ");
        } else {
            print0(ucase ? "CREATE PROCEDURE " : "create procedure ");
        }
        x.getName().accept(this);

        int paramSize = x.getParameters().size();

        if (paramSize > 0) {
            print0(" (");
            incrementIndent();
            println();

            for (int i = 0; i < paramSize; ++i) {
                if (i != 0) {
                    print0(", ");
                    println();
                }
                SQLParameter param = x.getParameters().get(i);
                param.accept(this);
            }

            decrementIndent();
            println();
            print(')');
        }

        SQLName authid = x.getAuthid();
        if (authid != null) {
            print(ucase ? " AUTHID " : " authid ");
            authid.accept(this);
        }

        SQLStatement block = x.getBlock();

        if (!create) {
            println();
            print("IS");
            println();
        } else {
            println();
            if (block instanceof SQLBlockStatement) {
                SQLBlockStatement blockStatement = (SQLBlockStatement) block;
                if (blockStatement.getParameters().size() > 0 || authid != null) {
                    println(ucase ? "AS" : "as");
                }
            }
        }

        String javaCallSpec = x.getJavaCallSpec();
        if (javaCallSpec != null) {
            print0(ucase ? "LANGUAGE JAVA NAME '" : "language java name '");
            print0(javaCallSpec);
            print('\'');
            return false;
        }

        block.accept(this);
        return false;
    }

    @Override
    public boolean visit(SQLCreateFunctionStatement x) {
        boolean create = x.isCreate();
        if (!create) {
            print0(ucase ? "FUNCTION " : "function ");
        } else if (x.isOrReplace()) {
            print0(ucase ? "CREATE OR REPLACE FUNCTION " : "create or replace function ");
        } else {
            print0(ucase ? "CREATE FUNCTION " : "create function ");
        }
        x.getName().accept(this);

        int paramSize = x.getParameters().size();

        if (paramSize > 0) {
            print0(" (");
            incrementIndent();
            println();

            for (int i = 0; i < paramSize; ++i) {
                if (i != 0) {
                    print0(", ");
                    println();
                }
                SQLParameter param = x.getParameters().get(i);
                param.accept(this);
            }

            decrementIndent();
            println();
            print(')');
        }

        println();
        print(ucase ? "RETURN " : "return ");
        x.getReturnDataType().accept(this);

        SQLName authid = x.getAuthid();
        if (authid != null) {
            print(ucase ? " AUTHID " : " authid ");
            authid.accept(this);
        }

        SQLStatement block = x.getBlock();

        if (!create) {
            println();
            println("IS");
        } else {
            println();
            if (block instanceof SQLBlockStatement) {
                SQLBlockStatement blockStatement = (SQLBlockStatement) block;
                if (blockStatement.getParameters().size() > 0 || authid != null) {
                    println(ucase ? "AS" : "as");
                }
            }
        }

        String javaCallSpec = x.getJavaCallSpec();
        if (javaCallSpec != null) {
            print0(ucase ? "LANGUAGE JAVA NAME '" : "language java name '");
            print0(javaCallSpec);
            print('\'');
            return false;
        }

        block.accept(this);
        return false;
    }

    @Override
    public void endVisit(SQLCreateProcedureStatement x) {

    }

    @Override
    public boolean visit(OracleCreateDatabaseDbLinkStatement x) {
        print0(ucase ? "CREATE " : "create ");
        if (x.isShared()) {
            print0(ucase ? "SHARE " : "share ");
        }

        if (x.isPublic()) {
            print0(ucase ? "PUBLIC " : "public ");
        }

        print0(ucase ? "DATABASE LINK " : "database link ");

        x.getName().accept(this);

        if (x.getUser() != null) {
            print0(ucase ? " CONNECT TO " : " connect to ");
            x.getUser().accept(this);

            if (x.getPassword() != null) {
                print0(ucase ? " IDENTIFIED BY " : " identified by ");
                print0(x.getPassword());
            }
        }

        if (x.getAuthenticatedUser() != null) {
            print0(ucase ? " AUTHENTICATED BY " : " authenticated by ");
            x.getAuthenticatedUser().accept(this);
            if (x.getAuthenticatedPassword() != null) {
                print0(ucase ? " IDENTIFIED BY " : " identified by ");
                print0(x.getAuthenticatedPassword());
            }
        }

        if (x.getUsing() != null) {
            print0(ucase ? " USING " : " using ");
            x.getUsing().accept(this);
        }

        return false;
    }

    @Override
    public void endVisit(OracleCreateDatabaseDbLinkStatement x) {

    }

    @Override
    public boolean visit(OracleDropDbLinkStatement x) {
        print0(ucase ? "DROP " : "drop ");
        if (x.isPublic()) {
            print0(ucase ? "PUBLIC " : "public ");
        }
        print0(ucase ? "DATABASE LINK " : "database link ");
        x.getName().accept(this);

        return false;
    }

    @Override
    public void endVisit(OracleDropDbLinkStatement x) {

    }

    public boolean visit(SQLCharacterDataType x) {
        print0(x.getName());
        if (x.getArguments().size() > 0) {
            print('(');
            x.getArguments().get(0).accept(this);
            if (x.getCharType() != null) {
                print(' ');
                print0(x.getCharType());
            }
            print(')');
        }
        return false;
    }

    @Override
    public boolean visit(OracleDataTypeTimestamp x) {
        print0(x.getName());
        if (x.getArguments().size() > 0) {
            print('(');
            x.getArguments().get(0).accept(this);
            print(')');
        }

        if (x.isWithTimeZone()) {
            print0(ucase ? " WITH TIME ZONE" : " with time zone");
        } else if (x.isWithLocalTimeZone()) {
            print0(ucase ? " WITH LOCAL TIME ZONE" : " with local time zone");
        }

        return false;
    }

    @Override
    public void endVisit(OracleDataTypeTimestamp x) {

    }

    @Override
    public boolean visit(OracleDataTypeIntervalYear x) {
        print0(x.getName());
        if (x.getArguments().size() > 0) {
            print('(');
            x.getArguments().get(0).accept(this);
            print(')');
        }

        print0(ucase ? " TO MONTH" : " to month");

        return false;
    }

    @Override
    public void endVisit(OracleDataTypeIntervalYear x) {

    }

    @Override
    public boolean visit(OracleDataTypeIntervalDay x) {
        print0(x.getName());
        if (x.getArguments().size() > 0) {
            print('(');
            x.getArguments().get(0).accept(this);
            print(')');
        }

        print0(ucase ? " TO SECOND" : " to second");

        if (x.getFractionalSeconds().size() > 0) {
            print('(');
            x.getFractionalSeconds().get(0).accept(this);
            print(')');
        }

        return false;
    }

    @Override
    public void endVisit(OracleDataTypeIntervalDay x) {

    }

    @Override
    public boolean visit(OracleUsingIndexClause x) {
        print0(ucase ? "USING INDEX" : "using index");
        if (x.getIndex() != null) {
            print(' ');
            x.getIndex().accept(this);
        }

        printOracleSegmentAttributes(x);

        if (x.isComputeStatistics()) {
            println();
            print0(ucase ? "COMPUTE STATISTICS" : "compute statistics");
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                println();
                print0(ucase ? "ENABLE" : "enable");
            } else {
                println();
                print0(ucase ? "DISABLE" : "disable");
            }
        }

        if (x.isReverse()) {
            println();
            print0(ucase ? "REVERSE" : "reverse");
        }


        return false;
    }

    @Override
    public void endVisit(OracleUsingIndexClause x) {

    }

    @Override
    public boolean visit(OracleLobStorageClause x) {
        print0(ucase ? "LOB (" : "lob (");
        printAndAccept(x.getItems(), ",");
        print0(ucase ? ") STORE AS" : ") store as");


        if (x.isSecureFile()) {
            print0(ucase ? " SECUREFILE" : " securefile");
        }

        if (x.isBasicFile()) {
            print0(ucase ? " BASICFILE" : " basicfile");
        }
        print0(" (");
        incrementIndent();
        printOracleSegmentAttributes(x);

        if (x.getEnable() != null) {
            println();
            if (x.getEnable().booleanValue()) {
                print0(ucase ? "ENABLE STORAGE IN ROW" : "enable storage in row");
            } else {
                print0(ucase ? "DISABLE STORAGE IN ROW" : "disable storage in row");
            }
        }

        if (x.getChunk() != null) {
            println();
            print0(ucase ? "CHUNK " : "chunk ");
            x.getChunk().accept(this);
        }

        if (x.getCache() != null) {
            println();
            if (x.getCache().booleanValue()) {
                print0(ucase ? "CACHE" : "cache");
            } else {
                print0(ucase ? "NOCACHE" : "nocache");
            }
        }

        if (x.getKeepDuplicate() != null) {
            println();
            if (x.getKeepDuplicate().booleanValue()) {
                print0(ucase ? "KEEP_DUPLICATES" : "keep_duplicates");
            } else {
                print0(ucase ? "DEDUPLICATE" : "deduplicate");
            }
        }

        if (x.isRetention()) {
            println();
            print0(ucase ? "RETENTION" : "retention");
        }

        decrementIndent();
        println();
        print(')');
        return false;
    }

    @Override
    public void endVisit(OracleLobStorageClause x) {

    }

    @Override
    public boolean visit(OracleUnique x) {
        visit((SQLUnique) x);

        printConstraintState(x);
        return false;
    }

    @Override
    public void endVisit(OracleUnique x) {

    }

    @Override
    public boolean visit(OracleForeignKey x) {
        visit((SQLForeignKeyImpl) x);

        printConstraintState(x);
        return false;
    }

    @Override
    public void endVisit(OracleForeignKey x) {

    }

    @Override
    public boolean visit(OracleCheck x) {
        visit((SQLCheck) x);

        printConstraintState(x);
        return false;
    }

    @Override
    public void endVisit(OracleCheck x) {

    }

    @Override
    protected void printCascade() {
        print0(ucase ? " CASCADE CONSTRAINTS" : " cascade constraints");
    }

    @Override
    public boolean visit(SQLMethodInvokeExpr x) {
        if ("trim".equalsIgnoreCase(x.getMethodName())) {
            SQLExpr trim_character = (SQLExpr) x.getAttribute("trim_character");
            if (trim_character != null) {
                print0(x.getMethodName());
                print('(');
                String trim_option = (String) x.getAttribute("trim_option");
                if (trim_option != null && trim_option.length() != 0) {
                    print0(trim_option);
                    print(' ');
                }
                trim_character.accept(this);
                if (x.getParameters().size() > 0) {
                    print0(ucase ? " FROM " : " from ");
                    x.getParameters().get(0).accept(this);
                }
                print(')');
                return false;
            }
        }

        return super.visit(x);
    }
    
    public boolean visit(SQLCharExpr x) {
        if (x.getText() != null && x.getText().length() == 0) {
            print0(ucase ? "NULL" : "null");
        } else {
            super.visit(x);
        }

        return false;
    }

    @Override
    public boolean visit(OracleSupplementalIdKey x) {
        print0(ucase ? "SUPPLEMENTAL LOG DATA (" : "supplemental log data (");

        int count = 0;

        if (x.isAll()) {
            print0(ucase ? "ALL" : "all");
            count++;
        }

        if (x.isPrimaryKey()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "PRIMARY KEY" : "primary key");
            count++;
        }

        if (x.isUnique()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "UNIQUE" : "unique");
            count++;
        }

        if (x.isUniqueIndex()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "UNIQUE INDEX" : "unique index");
            count++;
        }

        if (x.isForeignKey()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "FOREIGHN KEY" : "foreighn key");
            count++;
        }

        print0(ucase ? ") COLUMNS" : ") columns");
        return false;
    }

    @Override
    public void endVisit(OracleSupplementalIdKey x) {

    }

    @Override
    public boolean visit(OracleSupplementalLogGrp x) {
        print0(ucase ? "SUPPLEMENTAL LOG GROUP " : "supplemental log group ");
        x.getGroup().accept(this);
        print0(" (");
        printAndAccept(x.getColumns(), ", ");
        print(')');
        if (x.isAlways()) {
            print0(ucase ? " ALWAYS" : " always");
        }
        return false;
    }

    @Override
    public void endVisit(OracleSupplementalLogGrp x) {

    }

    public boolean visit(OracleCreateTableStatement.Organization x) {

        String type = x.getType();

        print0(ucase ? "ORGANIZATION " : "organization ");
        print0(ucase ? type : type.toLowerCase());

        printOracleSegmentAttributes(x);

        if (x.getPctthreshold() != null) {
            println();
            print0(ucase ? "PCTTHRESHOLD " : "pctthreshold ");
            print(x.getPctfree());
        }

        if ("EXTERNAL".equalsIgnoreCase(type)) {
            print0(" (");

            incrementIndent();
            if (x.getExternalType() != null) {
                println();
                print0(ucase ? "TYPE " : "type ");
                x.getExternalType().accept(this);
            }

            if (x.getExternalDirectory() != null) {
                println();
                print0(ucase ? "DEFAULT DIRECTORY " : "default directory ");
                x.getExternalDirectory().accept(this);
            }

            if (x.getExternalDirectoryRecordFormat() != null) {
                println();
                incrementIndent();
                print0(ucase ? "ACCESS PARAMETERS (" : "access parameters (");
                x.getExternalDirectoryRecordFormat().accept(this);
                decrementIndent();
                println();
                print(')');
            }

            if (x.getExternalDirectoryLocation().size() > 0) {
                println();
                print0(ucase ? "LOCATION (" : " location(");
                printAndAccept(x.getExternalDirectoryLocation(), ", ");
                print(')');
            }

            decrementIndent();
            println();
            print(')');

            if (x.getExternalRejectLimit() != null) {
                println();
                print0(ucase ? "REJECT LIMIT " : "reject limit ");
                x.getExternalRejectLimit().accept(this);
            }
        }

        return false;
    }

    public void endVisit(OracleCreateTableStatement.Organization x) {

    }

    public boolean visit(OracleCreateTableStatement.OracleExternalRecordFormat x) {
        if (x.getDelimitedBy() != null) {
            println();
            print0(ucase ? "RECORDS DELIMITED BY " : "records delimited by ");
            x.getDelimitedBy().accept(this);
        }

        if (x.getTerminatedBy() != null) {
            println();
            print0(ucase ? "FIELDS TERMINATED BY " : "fields terminated by ");
            x.getTerminatedBy().accept(this);
        }

        return false;
    }

    public void endVisit(OracleCreateTableStatement.OracleExternalRecordFormat x) {

    }

    public boolean visit(OracleCreateTableStatement.OIDIndex x) {
        print0(ucase ? "OIDINDEX" : "oidindex");

        if (x.getName() != null) {
            print(' ');
            x.getName().accept(this);
        }
        print(" (");
        incrementIndent();
        printOracleSegmentAttributes(x);
        decrementIndent();
        println();
        print(")");
        return false;
    }

    public void endVisit(OracleCreateTableStatement.OIDIndex x) {

    }

    @Override
    public boolean visit(OracleCreatePackageStatement x) {
        if (x.isOrReplace()) {
            print0(ucase ? "CREATE OR REPLACE PACKAGE " : "create or replace procedure ");
        } else {
            print0(ucase ? "CREATE PACKAGE " : "create procedure ");
        }

        if (x.isBody()) {
            print0(ucase ? "BODY " : "body ");
        }

        x.getName().accept(this);

        if (x.isBody()) {
            println();
            print0(ucase ? "BEGIN" : "begin");
        }

        incrementIndent();

        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            println();
            SQLStatement item = x.getStatements().get(i);
            item.setParent(x);
            item.accept(this);
        }

        decrementIndent();

        if (x.isBody()) {
            println();
            print0(ucase ? "END " : "end ");
            x.getName().accept(this);
            print(';');
        }

        return false;
    }

    @Override
    public void endVisit(OracleCreatePackageStatement x) {

    }

    @Override
    public boolean visit(SQLAssignItem x) {
        x.getTarget().accept(this);
        print0(" := ");
        x.getValue().accept(this);
        return false;
    }


    @Override
    public boolean visit(OracleExecuteImmediateStatement x) {
        print0(ucase ? "EXECUTE IMMEDIATE " : "execute immediate ");
        x.getDynamicSql().accept(this);

        List<SQLExpr> into = x.getInto();
        if (into.size() > 0) {
            print0(ucase ? " INTO " : " into ");
            printAndAccept(into, ", ");
        }

        List<SQLArgument> using = x.getArguments();
        if (using.size() > 0) {
            print0(ucase ? " USING " : " using ");
            printAndAccept(using, ", ");
        }

        List<SQLExpr> returnInto = x.getReturnInto();
        if (returnInto.size() > 0) {
            print0(ucase ? " RETURNNING INTO " : " returnning into ");
            printAndAccept(returnInto, ", ");
        }
        return false;
    }

    @Override
    public void endVisit(OracleExecuteImmediateStatement x) {

    }
}
