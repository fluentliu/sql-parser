package cn.ganjiacheng;

import cn.ganjiacheng.antlr.HiveSqlLexer;
import cn.ganjiacheng.antlr.HiveSqlParser;
import cn.ganjiacheng.hive.HiveSqlComplexParser;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CSVComplexReader extends SqlParserAbstract{

    private static final String sql = "select name,age,sum(income) from (select name,gender,age,income from table1 where name = \"Allen\") table2 group by name,age;";
    public static void main(String[] args) {
        CharStream input = CharStreams.fromString(sql);
        HiveSqlLexer lexer = new HiveSqlLexer(input);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        HiveSqlParser parser = new HiveSqlParser(tokenStream);
        ParseTree tree = parser.program();

        HiveSqlComplexParser visitor = new HiveSqlComplexParser(sql);
        visitor.visit(tree);
        //visitor.getHiveFieldSelects().get("0_table2");
        String resultSql = JSON.toJSONString(visitor.getHiveFieldSelects(), SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);
        //System.out.print("result:"+resultSql);
        List<String> tmpSelectList = new ArrayList<String>();
        int fieldSize = visitor.getHiveFieldSelects().get("0_table2").getSelectItems().size();
        for(int i =0;i<fieldSize-1;i++){
            tmpSelectList.add(JSON.toJSONString(visitor.getHiveFieldSelects().get("0_table2").getSelectItems().get(i).getAlias()));
            //System.out.println(JSON.toJSONString(visitor.getHiveFieldSelects().get("0_table2").getSelectItems().get(i).getAlias()));
        }
        String agg_func_item = JSON.toJSONString(visitor.getHiveFieldSelects().get("0_table2").getSelectItems().get(fieldSize-1).getAlias()).replace("\"","");


        List<String> selectList = new ArrayList<String>();
        for(String str:tmpSelectList){
            selectList.add(str.replace("\"",""));
        }
        Map<String,String> where_item = visitor.getWhere_item();

        //输出解析结果
        System.out.println("select_list:"+selectList+";\nagg_func_item"+agg_func_item);

        //读本地csv文件
        String csvFile = "/Users/zonghao.sun/Documents/table_data.csv";
        String line = "";
        String csvSplitBy = ",";
        int flag = 0;
        String[] csv_header=null;

        Map<String, Double> result = new HashMap<String,Double>();
        //获得表头数据
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            line = br.readLine();
            csv_header = line.split(csvSplitBy);
        }catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("csv_header:"+csv_header[3]);
        // select 所选的列
        List<Integer> index_arr = new ArrayList<Integer>();

        // agg-聚合 所选的列
        int index_value=0;
        // where 条件
        //System.out.println(where_item.size());
        List<Integer> where_index_arr = new ArrayList<Integer>();
        List<Object> where_value_arr = new ArrayList<Object>();

        for(String str:where_item.keySet()){
            for(int i = 0; i<csv_header.length;i++){
                if(str.equals(csv_header[i])){
                    where_index_arr.add(i);
                    where_value_arr.add(where_item.get(csv_header[i]).replace("\"",""));
                }
            }
        }

        System.out.println("where_index:"+where_index_arr+";"+where_value_arr);
        //System.out.println(where_value_arr.get(0));

        //select 所选列下标
        for(int i = 0;i<csv_header.length;i++){
            for(int j = 0;j<selectList.size();j++){
                if(csv_header[i].equals(selectList.get(j))){
                    index_arr.add(i);
                }
            }
        }

        // 聚合列下标
        for(int i = 0;i<csv_header.length;i++){
            //System.out.println("i:"+i+";csv_header:"+csv_header[i]+";AGG:"+agg_func_item);
            //bug:字符串比较==和equals含义不同
            if(csv_header[i].equals(agg_func_item)){
                index_value = i;
            }
        }
        //System.out.println("index_value:"+index_value);

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {

            while ((line = br.readLine()) != null) {
                // 忽略表头
                if(flag == 0){
                    //csv_header = line.split(csvSplitBy);
                    flag = 1;
                    continue;
                }
                // use comma as separator
                String[] country = line.split(csvSplitBy);
                //System.out.println("whereindex:");
                int where_flag = 0;
                for(int i = 0;i<where_index_arr.size();i++){
                    if(!country[where_index_arr.get(i)].equals(where_value_arr.get(i))){
                        where_flag = 1;
                    }
                }

                if(where_flag == 0){
                    //筛选出select的列
                    String key = country[index_arr.get(0)];

                    //System.out.println("index_arr:"+index_arr.size());
                    for(int i = 1;i<index_arr.size();i++){
                        key = key + "," + country[index_arr.get(i)];
                    }
                    //根据分组来求和
                    Double value = Double.valueOf(country[index_value]);

                    if(result.containsKey(key)){
                        result.put(key,result.get(key)+value);
                    }else{
                        result.put(key,value);
                    }
                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(Map.Entry<String,Double> entry:result.entrySet()){
            System.out.println(entry.getKey()+","+entry.getValue());
        }

    }
}
