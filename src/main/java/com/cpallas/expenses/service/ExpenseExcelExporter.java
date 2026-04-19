package com.cpallas.expenses.service;

import com.cpallas.expenses.service.dto.ExpenseExportRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ExpenseExcelExporter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static byte[] exportExpensesToExcel(List<ExpenseExportRow> expenses) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Expenses");

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {"Сумма траты", "Категория", "Описание траты", "Дата записи"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int nextRowIdx = 1;
            for (ExpenseExportRow expense : expenses) {
                Row row = sheet.createRow(nextRowIdx++);

                row.createCell(0).setCellValue(expense.amount().doubleValue());
                row.createCell(1).setCellValue(expense.categoryName());
                row.createCell(2).setCellValue(expense.description());
                row.createCell(3).setCellValue(expense.createdAt().format(DATE_TIME_FORMATTER));
            }
            sheet.createRow(nextRowIdx++); //empty row
            summarizeByCategories(expenses, sheet, nextRowIdx);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void summarizeByCategories(List<ExpenseExportRow> expenses, Sheet sheet, int nextRowIdx) {
        Map<String, BigDecimal> result = expenses.stream()
                .collect(Collectors.groupingBy(
                        ExpenseExportRow::categoryName,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                ExpenseExportRow::amount,
                                BigDecimal::add
                        )
                ));

        for (Map.Entry<String, BigDecimal> entry : result.entrySet()) {
            Row row = sheet.createRow(nextRowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue().doubleValue());
        }
    }
}
