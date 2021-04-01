package cn.ganjiacheng;

import cn.ganjiacheng.antlr.HiveSqlLexer;
import cn.ganjiacheng.antlr.HiveSqlParser;
import cn.ganjiacheng.hive.HiveSqlSimpleParser;
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

public class CSVReader extends SqlParserAbstract{

    private static final String sql = "select name,age,sum(income) from table1 where name = \"Allen\" and gender = \"male\" group by name,age;";
    //private static final String sql = "select name,sum(income) from table1 where name = \"Allen\" and gender = \"male\" group by name;";
    public static void main(String[] args) {
        CharStream input = CharStreams.fromString(sql);
        HiveSqlLexer lexer = new HiveSqlLexer(input);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        HiveSqlParser parser = new HiveSqlParser(tokenStream);
        ParseTree tree = parser.program();

        HiveSqlSimpleParser visitor = new HiveSqlSimpleParser(sql);
        visitor.visit(tree);

        List<String> select_list = visitor.getSelect_item();
        String agg_func = visitor.getAgg_func();
        String agg_func_item = visitor.getAgg_func_item();
        String from_item = visitor.getFrom_item();
        Map<String,String> where_item = visitor.getWhere_item();
        //输出解析结果
        System.out.println("select_list:"+select_list+";\nagg_func:"+agg_func+";\nagg_func_item:"+agg_func_item+";\nfrom_item:"+from_item+";\nwhere_item:"+where_item);

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
            for(int j = 0;j<select_list.size();j++){
                if(csv_header[i].equals(select_list.get(j))){
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
                int where_flag = 0;
                for(int i = 0;i<where_index_arr.size();i++){
                    //System.out.println("test:"+country[where_index_arr.get(i)]+";"+where_value_arr.get(i));
                    if(!country[where_index_arr.get(i)].equals(where_value_arr.get(i))){
                        where_flag = 1;
                    }
                }
                if(where_flag == 0){
                    //筛选出select的列
                    String key = country[index_arr.get(0)];
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
                /*第一版：写死
                String key = country[1]+","+country[2];
                Double value = Double.valueOf(country[3]);
                if(result.containsKey(key)){
                    result.put(key,result.get(key)+value);
                }else{
                    result.put(key,value);
                }
                result.put(country[1]+country[2],Double.valueOf(country[3]));
                */
                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
        for(String key:result.keySet()){
            System.out.println(key);
        }
        for(Double value:result.values()){
            System.out.println(value);
        }
        */
        for(Map.Entry<String,Double> entry:result.entrySet()){
            System.out.println(entry.getKey()+","+entry.getValue());
        }
    }
}
