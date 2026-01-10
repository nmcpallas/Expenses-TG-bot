package com.cpallas.expenses.service;

import com.cpallas.expenses.service.dto.ExpenseExportRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ExpenseExcelExporter {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static byte[] exportExpensesToExcel(List<ExpenseExportRow> expenses) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Expenses");

            // --- 1. Header row ---
            Row headerRow = sheet.createRow(0);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {"Amount", "Category", "Description", "Created At"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // --- 2. Data rows ---
            int rowIdx = 1;
            for (ExpenseExportRow expense : expenses) {
                Row row = sheet.createRow(rowIdx++);

                // Amount
                Cell amountCell = row.createCell(0);
                if (expense.amount() != null) {
                    amountCell.setCellValue(expense.amount());
                }

                // Category name (предполагаю, что у CategoryJpa есть getName())
                String categoryName = expense.categoryName();
                if (expense.categoryName() != null) {
                    // поправь на реально существующий геттер, если он другой
                    categoryName = expense.categoryName();
                }
                row.createCell(1).setCellValue(categoryName != null ? categoryName : "");

                // Description
                row.createCell(2).setCellValue(
                        expense.description() != null ? expense.description() : ""
                );

                // Created At (как строка)
                String createdAtStr = "";
                if (expense.createdAt() != null) {
                    createdAtStr = expense.createdAt().format(DATE_TIME_FORMATTER);
                }
                row.createCell(3).setCellValue(createdAtStr);
            }

            // --- 3. Auto-size columns ---
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
