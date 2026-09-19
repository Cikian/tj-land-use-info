-- add by lf_20240314 修改符号化和标注的数据类型
ALTER TABLE estar_cim_layersymbol CHANGE configTable configTable LONGTEXT;
ALTER TABLE estar_cim_layersymbol CHANGE segmentTable segmentTable LONGTEXT;
ALTER TABLE estar_cim_layersymbol CHANGE singleimg singleimg LONGTEXT;
ALTER TABLE estar_cim_layersymbol CHANGE uniqueimg uniqueimg LONGTEXT;
ALTER TABLE estar_cim_layersymbol CHANGE segmentimg segmentimg LONGTEXT;

ALTER TABLE estar_cim_textrender CHANGE configTable configTable LONGTEXT;
ALTER TABLE estar_cim_textrender CHANGE segmentTable segmentTable LONGTEXT;
ALTER TABLE estar_cim_textrender CHANGE singleimg singleimg LONGTEXT;
ALTER TABLE estar_cim_textrender CHANGE uniqueimg uniqueimg LONGTEXT;
ALTER TABLE estar_cim_textrender CHANGE segmentimg segmentimg LONGTEXT;