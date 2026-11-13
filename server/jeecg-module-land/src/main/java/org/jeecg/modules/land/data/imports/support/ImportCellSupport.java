package org.jeecg.modules.land.data.imports.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFCell;

import java.util.Locale;

/**
 * 宗地、配套导入共用的单元格读取器。
 *
 * <p>公式只读取 Excel 文件里已经保存的缓存结果，不创建 FormulaEvaluator，
 * 因此不会执行外部工作簿链接，也不会把公式文本误送进数字、日期或枚举校验。</p>
 */
public final class ImportCellSupport {

    private ImportCellSupport() {
    }

    /**
     * 读取一个单元格。公式无缓存、空缓存或错误缓存时返回明确错误，由调用方补上行号与字段名。
     */
    public static CellValue read(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return CellValue.text("");
        }
        if (cell.getCellType() != CellType.FORMULA) {
            return CellValue.text(formatter.formatCellValue(cell));
        }
        if (cell instanceof XSSFCell && !((XSSFCell) cell).getCTCell().isSetV()) {
            return CellValue.error("公式没有已保存的缓存结果，请在 Excel 中重新计算并保存后再导入");
        }

        CellType cachedType;
        try {
            cachedType = cell.getCachedFormulaResultType();
        } catch (RuntimeException e) {
            return CellValue.error("公式缓存结果无法读取，请在 Excel 中重新计算并保存后再导入");
        }
        switch (cachedType) {
            case NUMERIC:
                return CellValue.text(formatter.formatRawCellContents(
                        cell.getNumericCellValue(), cell.getCellStyle().getDataFormat(),
                        cell.getCellStyle().getDataFormatString()));
            case STRING:
                String value = cell.getStringCellValue();
                if (value == null || value.trim().isEmpty()) {
                    return CellValue.error("公式缓存结果为空，请在 Excel 中重新计算并保存后再导入");
                }
                return CellValue.text(value);
            case BOOLEAN:
                return CellValue.text(String.valueOf(cell.getBooleanCellValue()).toUpperCase(Locale.ROOT));
            case ERROR:
                return CellValue.error("公式缓存结果为 Excel 错误值（错误码 "
                        + cell.getErrorCellValue() + "），请修正公式并重新保存后再导入");
            case BLANK:
            case _NONE:
            default:
                return CellValue.error("公式没有可用的缓存结果，请在 Excel 中重新计算并保存后再导入");
        }
    }

    /** 单元格文本与公式读取错误二者必居其一。 */
    public static final class CellValue {
        private final String text;
        private final String error;

        private CellValue(String text, String error) {
            this.text = text;
            this.error = error;
        }

        private static CellValue text(String text) {
            return new CellValue(text == null ? "" : text, null);
        }

        private static CellValue error(String error) {
            return new CellValue("", error);
        }

        public String getText() {
            return text;
        }

        public String getError() {
            return error;
        }

        public boolean hasError() {
            return error != null;
        }
    }
}
