package com.drivemate.showcase.domain.usecase.report

import com.drivemate.showcase.core.util.CsvWriter

data class ReportCsvSection(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class ExportReportCsvRequest(
    val sections: List<ReportCsvSection>
)

class ExportReportCsvUseCase {

    operator fun invoke(request: ExportReportCsvRequest): String {
        if (request.sections.isEmpty()) return ""

        return request.sections.joinToString(separator = CSV_SECTION_SEPARATOR) { section ->
            buildString {
                append(CsvWriter.escapeCsvCell(section.title))
                appendLine()
                append(CsvWriter.tableToCsv(section.headers, section.rows))
            }
        }
    }

    private companion object {
        private const val CSV_SECTION_SEPARATOR = "\n\n"
    }
}
