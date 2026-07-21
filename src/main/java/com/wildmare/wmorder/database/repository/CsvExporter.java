package com.wildmare.wmorder.database.repository;

import com.wildmare.wmorder.database.DatabaseManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.List;

public final class CsvExporter {
    private final DatabaseManager database;
    public CsvExporter(DatabaseManager database){this.database=database;}
    public long exportOrders(File file){
        file.getParentFile().mkdirs();long rows=0;
        try(Connection c=database.connection();PreparedStatement ps=c.prepareStatement("SELECT id,buyer_uuid,buyer_name,item_material,item_display_name,requested_quantity,remaining_quantity,fulfilled_quantity,price_per_item,original_total,remaining_reserved_balance,created_at,expires_at,status,category,server_id,version FROM wm_orders ORDER BY created_at DESC");ResultSet rs=ps.executeQuery();
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8))){
            List<String> headers=List.of("id","buyer_uuid","buyer_name","item_material","item_display_name","requested_quantity","remaining_quantity","fulfilled_quantity","price_per_item","original_total","remaining_reserved_balance","created_at","expires_at","status","category","server_id","version");
            out.write(String.join(",",headers));out.newLine();while(rs.next()){for(int i=1;i<=headers.size();i++){if(i>1)out.write(',');out.write(escape(rs.getString(i)));}out.newLine();rows++;}
            return rows;
        }catch(SQLException|IOException e){throw new DatabaseManager.DatabaseException(e);}
    }
    private String escape(String value){if(value==null)return "";String v=value.replace("\"","\"\"");return v.indexOf(',')>=0||v.indexOf('\n')>=0||v.indexOf('"')>=0?'"'+v+'"':v;}
}
