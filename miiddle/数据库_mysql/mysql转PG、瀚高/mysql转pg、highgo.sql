-- 大写的表名和字段名称转小写
-- 创建exec函数
CREATE OR REPLACE FUNCTION "exec"("sqlstring" varchar)
  RETURNS "pg_catalog"."varchar" AS $BODY$
    declare
        res varchar(50);
    BEGIN
        EXECUTE sqlstring;
        RETURN 'ok';
    END
$BODY$
  LANGUAGE plpgsql VOLATILE
  COST 100;



-- 根据条件查询所有大写的column
select * from information_schema.columns where table_schema='webgl_zk' and table_name<>'pg_stat_statements' and column_name <> lower(column_name);

-- 修改条件后，通过下列语句转换column_name中的大写字母为小写
SELECT
    exec('alter table "' || table_name || '" rename column  "' || column_name || '" to ' || lower( column_name ) || ';')
FROM
    information_schema.COLUMNS 
WHERE
    table_schema = 'webgl_zk' 
    AND column_name <> lower(column_name);



-- 修改字段的数据类型，字符型修改为数值型，防止比较大小时候报错
ALTER TABLE stargis_layer ALTER COLUMN shareway TYPE integer USING shareway::integer;
ALTER TABLE stargis_function ALTER COLUMN shareway TYPE integer USING shareway::integer;
ALTER TABLE stargis_scheme ALTER COLUMN shareway TYPE integer USING shareway::integer;
ALTER TABLE stargis_metadata ALTER COLUMN shareway TYPE integer USING shareway::integer;
ALTER TABLE stargis_funbasicinfo ALTER COLUMN shareway TYPE integer USING shareway::integer;


-- 修改字段长度
ALTER TABLE jbpm4_variable alter COLUMN hist_ type varchar(10);
ALTER TABLE estar_jbpm4path_system alter COLUMN stateis type varchar(10);
ALTER TABLE jbpm4_task alter COLUMN signalling_ type varchar(10);
ALTER TABLE estar_flow_system alter COLUMN flowtimetype type varchar(10);
ALTER TABLE stargis_layersymbol alter COLUMN usebottonheight type varchar(10);
ALTER TABLE stargis_layersymbol alter COLUMN usebottonheight type varchar(10);
ALTER TABLE stargis_layersymbol alter COLUMN usingsymbol type varchar(10);
ALTER TABLE stargis_layersymbol alter COLUMN use3d type varchar(10);
ALTER TABLE jbpm4_job alter COLUMN isexclusive_ type varchar(10);
ALTER TABLE jbpm4_task alter COLUMN hasvars_ type varchar(10);
ALTER TABLE jbpm4_task alter COLUMN class_ type varchar(10);