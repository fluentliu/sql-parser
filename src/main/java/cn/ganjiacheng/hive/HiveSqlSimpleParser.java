package cn.ganjiacheng.hive;

import cn.ganjiacheng.antlr.HiveSqlBaseVisitor;
import cn.ganjiacheng.antlr.HiveSqlParser;
import cn.ganjiacheng.model.lineage.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @ClassName HiveSqlFieldLineage
 * @description:
 * @author: sunzonghao
 * @Date: 2021/03/31 下午
 */
public class HiveSqlSimpleParser extends HiveSqlBaseVisitor {

    private TableNameModel outputTable;

    private final HashMap<String, FieldLineageSelectModel> hiveFieldSelects = new LinkedHashMap<>();

    private final Map<Integer, String> selectParentKeyMap = new HashMap<>();

    private String thisSelectId;

    private final String sourceSQL;

    //select 列表
    private List<String> select_item=new ArrayList<>();
    //聚合函数
    private String agg_func;
    //聚合列
    private String agg_func_item;
    // from 表
    private String from_item;
    // where 条件列表
    private Map<String,Object> where_item = new HashMap<String,Object>();
    // group by分组列表
    private List<String> group_by_item = new ArrayList<>();


    /**
     * for select Item
     */
    private FieldLineageSelectItemModel selectItemModel;
    private List<FieldLineageSelectItemModel> selectFields = new ArrayList<>();
    private Boolean startSelectItem = false;

    public HiveSqlSimpleParser(String sql) {
        this.sourceSQL = sql;;
    }

    private String subSourceSql(ParserRuleContext parserRuleContext) {
        return sourceSQL.substring(
                parserRuleContext.getStart().getStartIndex(),
                parserRuleContext.getStop().getStopIndex() + 1);
    }


    /**
     * 解析select每个selectItem里用到字段
     */
    @Override
    public Object visitExpr(HiveSqlParser.ExprContext ctx) {
        //String str = ctx.expr_atom().ident().getText();
        //select_item.add(str);
        return super.visitExpr(ctx);
    }

    /**
     * 获取select字段
     */
    @Override
    public Object visitSelect_list_item(HiveSqlParser.Select_list_itemContext ctx) {

        /*
        if (ctx.expr().children instanceof HiveSqlParser.Expr_agg_window_funcContext){
            agg_func_item = ctx.expr().expr_agg_window_func().expr(0).expr_atom().ident().getText();
            //agg_func_item = ctx.expr().expr_agg_window_func().expr(0).expr_atom().ident().getText();
        }
        */
        //agg_func_item = ctx.expr().expr_agg_window_func().T_SUM().getText();
        if(ctx.expr().expr_agg_window_func() != null) {
            agg_func = ctx.expr().expr_agg_window_func().T_SUM().getText();
            agg_func_item = ctx.expr().expr_agg_window_func().expr(0).getText();
        }else{
            String str = ctx.expr().expr_atom().ident().getText();
            select_item.add(str);
        }
        //}

        /*
        if(ctx.expr().children instanceof HiveSqlParser.Expr_agg_window_funcContext){
            //System.out.println("visit expr_agg_window:"+ctx.expr().expr_agg_window_func().getText());
            agg_func_item = ctx.expr().expr_agg_window_func().getText();
        }
        //System.out.println("cgx.expr.children: "+ ctx.expr());
        if(ctx.expr().children instanceof HiveSqlParser.Expr_atomContext){
            System.out.println("visit expr_atom:" + ctx.expr().expr_atom().ident().getText());
            String str = ctx.expr().expr_atom().ident().getText();
            select_item.add(str);
        }

         */
        //agg_func_item = ctx.expr().getText();
        return super.visitSelect_list_item(ctx);
    }

    public List<String> getSelect_item(){
        return select_item;
    };

    public String getAgg_func_item(){
        return agg_func_item;
    }
    public String getAgg_func(){
        return agg_func;
    }
    /**
     * from语句
     */
    @Override
    public Object visitFrom_clause(HiveSqlParser.From_clauseContext ctx) {
        from_item = ctx.from_table_clause().from_table_name_clause().table_name().ident().getText();
        return super.visitFrom_clause(ctx);
    }

    public String getFrom_item(){
        return from_item;
    }


    @Override
    public Object visitWhere_clause(HiveSqlParser.Where_clauseContext ctx){
        //System.out.println();
        //System.out.println(ctx.bool_expr().children.size());
        //int bool_expr_size = ctx.bool_expr().children.size();
        //System.out.println("bool_expr_size:"+bool_expr_size);
        visitBool_expr(ctx.bool_expr());

        //String key = ctx.bool_expr().bool_expr_atom().bool_expr_binary().expr(0).expr_atom().ident().getText();
        //Object value = ctx.bool_expr().bool_expr_atom().bool_expr_binary().expr(1).expr_atom().ident().getText();
        //where_item.put(key,value);
        return super.visitWhere_clause(ctx);
    }
    @Override
    //递归访问
    public Object visitBool_expr(HiveSqlParser.Bool_exprContext ctx){
        if(ctx.children.size()==3){
            visitBool_expr(ctx.bool_expr(0));
            visitBool_expr(ctx.bool_expr(1));
        }else{
            String key = ctx.bool_expr_atom().bool_expr_binary().expr(0).expr_atom().ident().getText();
            Object value = ctx.bool_expr_atom().bool_expr_binary().expr(1).expr_atom().ident().getText();
            where_item.put(key,value);
        }

        return super.visitBool_expr(ctx);
    }


    public Map<String,Object> getWhere_item(){
        return where_item;
    }


    @Override
    //暂时不考虑
    public Object visitGroup_by_clause(HiveSqlParser.Group_by_clauseContext ctx){
        return super.visitGroup_by_clause(ctx);
    }
    /**
     * 进入select前
     * 解析每个select存信息并另存父子关系
     * 父子来源于from subSelect, join subSelect
     */
    @Override
    public Object visitSelect_stmt(HiveSqlParser.Select_stmtContext ctx) {
        List<HiveSqlParser.Fullselect_stmt_itemContext> selectItems = ctx.fullselect_stmt().fullselect_stmt_item();
        for (HiveSqlParser.Fullselect_stmt_itemContext selectItem : selectItems) {
            FieldLineageSelectModel fieldLineageSelectModel = new FieldLineageSelectModel();
            Integer thisId = selectItem.getStart().getStartIndex();
            HiveSqlParser.Subselect_stmtContext subSelect = selectItem.subselect_stmt();
            HiveSqlParser.From_table_name_clauseContext fromTableNameClause = Optional.ofNullable(subSelect)
                    .map(HiveSqlParser.Subselect_stmtContext::from_clause)
                    .map(HiveSqlParser.From_clauseContext::from_table_clause)
                    .map(HiveSqlParser.From_table_clauseContext::from_table_name_clause)
                    .orElse(null);
            Optional.ofNullable(fromTableNameClause)
                    .map(HiveSqlParser.From_table_name_clauseContext::table_name)
                    .map(RuleContext::getText)
                    .map(TableNameModel::parseTableName)
                    .ifPresent(fieldLineageSelectModel::setFromTable);
            Optional.ofNullable(fromTableNameClause)
                    .map(HiveSqlParser.From_table_name_clauseContext::from_alias_clause)
                    .map(HiveSqlParser.From_alias_clauseContext::ident)
                    .map(RuleContext::getText)
                    .ifPresent(fieldLineageSelectModel::setTableAlias);

            Optional.ofNullable(subSelect)
                    .map(HiveSqlParser.Subselect_stmtContext::from_clause)
                    .map(HiveSqlParser.From_clauseContext::from_table_clause)
                    .map(HiveSqlParser.From_table_clauseContext::from_subselect_clause)
                    .map(HiveSqlParser.From_subselect_clauseContext::from_alias_clause)
                    .map(RuleContext::getText)
                    .ifPresent(fieldLineageSelectModel::setTableAlias);

            String alias = fieldLineageSelectModel.getTableAlias();
            String thisKey = String.format("%s_%s", thisId, alias == null ? "" : alias);
            fieldLineageSelectModel.setId(thisKey + "");
            fieldLineageSelectModel.setParentId(selectParentKeyMap.get(thisId));
            fieldLineageSelectModel.setSelectItems(new ArrayList<>());
            hiveFieldSelects.put(thisKey, fieldLineageSelectModel);

            Optional.ofNullable(subSelect)
                    .map(HiveSqlParser.Subselect_stmtContext::from_clause)
                    .map(HiveSqlParser.From_clauseContext::from_table_clause)
                    .map(HiveSqlParser.From_table_clauseContext::from_subselect_clause)
                    .map(HiveSqlParser.From_subselect_clauseContext::select_stmt)
                    .map(HiveSqlParser.Select_stmtContext::fullselect_stmt)
                    .map(HiveSqlParser.Fullselect_stmtContext::fullselect_stmt_item)
                    .ifPresent(subSelects ->
                            subSelects.forEach(item ->
                                    selectParentKeyMap.put(item.getStart().getStartIndex(), thisKey)));

            List<HiveSqlParser.From_join_clauseContext> fromJoinClauses = Optional.ofNullable(subSelect)
                    .map(HiveSqlParser.Subselect_stmtContext::from_clause)
                    .map(HiveSqlParser.From_clauseContext::from_join_clause)
                    .orElse(new ArrayList<>());
            for (HiveSqlParser.From_join_clauseContext fromJoinClauseContext : fromJoinClauses) {
                FieldLineageSelectModel joinSelect = new FieldLineageSelectModel();
                Optional.ofNullable(fromJoinClauseContext)
                        .map(HiveSqlParser.From_join_clauseContext::from_table_clause)
                        .map(HiveSqlParser.From_table_clauseContext::from_table_name_clause)
                        .map(HiveSqlParser.From_table_name_clauseContext::table_name)
                        .map(RuleContext::getText)
                        .map(TableNameModel::parseTableName)
                        .ifPresent(joinSelect::setFromTable);
                Optional.ofNullable(fromJoinClauseContext)
                        .map(HiveSqlParser.From_join_clauseContext::from_table_clause)
                        .map(HiveSqlParser.From_table_clauseContext::from_table_name_clause)
                        .map(HiveSqlParser.From_table_name_clauseContext::from_alias_clause)
                        .map(HiveSqlParser.From_alias_clauseContext::ident)
                        .map(RuleContext::getText)
                        .ifPresent(joinSelect::setTableAlias);

                Optional.ofNullable(fromJoinClauseContext)
                        .map(HiveSqlParser.From_join_clauseContext::from_table_clause)
                        .map(HiveSqlParser.From_table_clauseContext::from_subselect_clause)
                        .map(HiveSqlParser.From_subselect_clauseContext::from_alias_clause)
                        .map(RuleContext::getText)
                        .ifPresent(joinSelect::setTableAlias);

                String jalias = joinSelect.getTableAlias();
                String jkey = String.format("%s_%s", thisId, jalias == null ? "" : jalias);
                joinSelect.setId(jkey);
                joinSelect.setParentId(selectParentKeyMap.get(thisId));
                joinSelect.setSelectItems(new ArrayList<>());
                hiveFieldSelects.put(jkey, joinSelect);

                Optional.ofNullable(fromJoinClauseContext)
                        .map(HiveSqlParser.From_join_clauseContext::from_table_clause)
                        .map(HiveSqlParser.From_table_clauseContext::from_subselect_clause)
                        .map(HiveSqlParser.From_subselect_clauseContext::select_stmt)
                        .map(HiveSqlParser.Select_stmtContext::fullselect_stmt)
                        .map(HiveSqlParser.Fullselect_stmtContext::fullselect_stmt_item)
                        .ifPresent(subSelects ->
                                subSelects.forEach(item ->
                                        selectParentKeyMap.put(item.getStart().getStartIndex(), jkey)));
            }
        }
        return super.visitSelect_stmt(ctx);
    }

    /**
     * 处理每个子select进入前，
     * 初始化selectItem相关的变量
     */
    @Override
    public Object visitSubselect_stmt(HiveSqlParser.Subselect_stmtContext ctx) {
        thisSelectId = ctx.getStart().getStartIndex() + "";
        selectFields = new ArrayList<>();
        return super.visitSubselect_stmt(ctx);
    }

    private final List<FieldLineageSelectModel> hiveFieldSelectList = new ArrayList<>();

    /**
     * 转换HashMap存储为List
     */
    private void transSelectToList() {
        for (String key : hiveFieldSelects.keySet()) {
            hiveFieldSelectList.add(hiveFieldSelects.get(key));
        }
    }

    /**
     * 获取目标字段
     * 也就是parentId为null的最外层select的字段别名
     */
    private List<FieldNameModel> getTargetFields() {
        List<List<String>> items = hiveFieldSelectList.stream()
                .filter(item -> item.getParentId() == null)
                .map(FieldLineageSelectModel::getSelectItems)
                .map(fields -> fields.stream()
                        .map(FieldLineageSelectItemModel::getAlias)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        List<String> res = new ArrayList<>();
        for (List<String> item : items) {
            res.addAll(item);
        }
        res = res.stream().distinct().collect(Collectors.toList());
        List<FieldNameModel> fieldNameModels = new ArrayList<>();
        for (String i : res) {
            FieldNameModel fieldNameModel = new FieldNameModel();
            if (outputTable != null) {
                fieldNameModel.setDbName(outputTable.getDbName());
                fieldNameModel.setTableName(outputTable.getTableName());
            }
            fieldNameModel.setFieldName(i);
            fieldNameModels.add(fieldNameModel);
        }
        return fieldNameModels;
    }

    private HashSet<FieldNameWithProcessModel> sourceFields;
    private String fieldProcess = "";

    /**
     * 递归按每个字段从外到内寻找每个字段的来源
     * 逻辑为最外的字段别名，父id -> 匹配子id别名 ->
     * -> 如果是来源是表，存储，如果来源是子select，继续递归
     */
    private void findFieldSource(String targetField, String parentId) {
        hiveFieldSelectList.forEach(select -> {
            if ((parentId == null && select.getParentId() == null) ||
                    (select.getParentId() != null && select.getParentId().equals(parentId))) {
                if (select.getSelectItems() != null) {
                    if (select.getFromTable() == null) {
                        select.getSelectItems().forEach(selectItem -> {
                            if (selectItem.getAlias().equals(targetField)) {
                                if (selectItem.getProcess().length() > fieldProcess.length()) {
                                    fieldProcess = selectItem.getProcess();
                                }
                                for (String field : selectItem.getFieldNames()) {
                                    findFieldSource(field, select.getId());
                                }
                            }
                        });
                    } else {
                        select.getSelectItems().forEach(selectItem -> {
                            if (selectItem.getAlias().equals(targetField)) {
                                if (selectItem.getProcess().length() > fieldProcess.length()) {
                                    fieldProcess = selectItem.getProcess();
                                }
                                for (String field : selectItem.getFieldNames()) {
                                    FieldNameWithProcessModel fieldNameWithProcessModel = new FieldNameWithProcessModel();
                                    fieldNameWithProcessModel.setDbName(select.getFromTable().getDbName());
                                    fieldNameWithProcessModel.setTableName(select.getFromTable().getTableName());
                                    fieldNameWithProcessModel.setFieldName(field);
                                    fieldNameWithProcessModel.setProcess(fieldProcess);
                                    sourceFields.add(fieldNameWithProcessModel);
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * 获取字段血缘列表
     */
    public List<FieldLineageModel> getHiveFieldLineage() {
        transSelectToList();
        List<FieldNameModel> targetFields = getTargetFields();
        List<FieldLineageModel> fieldLineageModelList = new ArrayList<>();
        for (FieldNameModel targetField : targetFields) {
            FieldLineageModel fieldLineageModel = new FieldLineageModel();
            fieldLineageModel.setTargetField(targetField);
            sourceFields = new HashSet<>();
            fieldProcess = "";
            findFieldSource(targetField.getFieldName(), null);
            fieldLineageModel.setSourceFields(sourceFields);
            fieldLineageModelList.add(fieldLineageModel);
        }
        return fieldLineageModelList;
    }

    /**
     * 获取sql解析处理后的结果
     */
    public HashMap<String, FieldLineageSelectModel> getHiveFieldSelects() {
        return hiveFieldSelects;
    }
}
