package cn.ganjiacheng;

import cn.ganjiacheng.antlr.HiveSqlLexer;
import cn.ganjiacheng.antlr.HiveSqlParser;
import cn.ganjiacheng.hive.HiveSqlSimpleParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;


public class test extends SqlParserAbstract {
    //private static final String sql = "create table if not exists db_test.t_user_visit_di (user_id string '用户',page_id string comment '页面');";
    //private static final String sql = "select user_id,page_id from dwd.t_user_visit_di where pt_day = '2021-01-01' and user_id = 'parrt';";
    //private static final String sql = "select user_id,page_id from (select user_id,page_id from dwd.t_user_visit_di where pt_day = '2021-01-01') t1 where t1.user_id = 'parrt';";
    //private static final String sql = "insert overwrite table dwd.t_user_visit_di select * from source;";
    /*
    private static final String sql = "select \n" +
            "name,\n" +
            "age,\n" +
            "sum(income) \n" +
            "from \n" +
            "(select \n" +
            "gender,\n" +
            "name,\n" +
            "age,\n" +
            "income \n" +
            "from \n" +
            "table1 \n" +
            "where \n" +
            "name = “Allen”\n" +
            ") table2\n" +
            "group by \n" +
            "name,age\n";
     */
    private static final String sql = "select name,age,sum(income) from table1 where name = \"Allen\" and gender = \"male\" group by name,age;";
    public static void main(String[] args) {
        CharStream input = CharStreams.fromString(sql);
        HiveSqlLexer lexer = new HiveSqlLexer(input);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        HiveSqlParser parser = new HiveSqlParser(tokenStream);
        ParseTree tree = parser.program();

        HiveSqlSimpleParser visitor = new HiveSqlSimpleParser(sql);
        visitor.visit(tree);
        System.out.println("select:" + visitor.getSelect_item());
        System.out.println("agg_func:" + visitor.getAgg_func());
        System.out.println("agg_func_item:" + visitor.getAgg_func_item());
        System.out.println("from:" + visitor.getFrom_item());
        System.out.println("where:" + visitor.getWhere_item());
        /*
        // sql类型
        HiveSqlTypeParser visitor = new HiveSqlTypeParser();
        visitor.visit(tree);
        System.out.println(visitor.getSqlType());


        //元数据
        HiveSqlMetadataParser visitor  = new HiveSqlMetadataParser(sql);
        visitor.visit(tree);
        System.out.println(visitor.getTableMetadata());
        System.out.println(JSON.toJSONString(visitor.getTableMetadata(), SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat));



        // 格式化
        HiveSqlFormatterParser visitor = new HiveSqlFormatterParser(sql);
        visitor.visit(tree);
        System.out.println(visitor.getFormattedSQL());
        System.out.println(visitor.getFirstSelect());
        


        HiveSqlSelectParser visitor = new HiveSqlSelectParser(sql);
        visitor.visit(tree);
        //System.out.println(visitor.getHiveFieldLineage());
        //System.out.println(JSON.toJSONString(JSON.toJSONString(visitor.getHiveFieldLineage()), SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat));
        System.out.println(JSON.toJSONString(JSON.toJSONString(visitor.getHiveFieldSelects()), SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat));
        */


    }

}
