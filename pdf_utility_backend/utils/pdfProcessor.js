const fs = require('fs');
const {PDFDocument, rgb, StandardFonts} = require('pdf-lib');

async function compress_PDF(inputPath, outputPath) {
    const pdfBytes = fs.readFileSync(inputPath);
    const pdfDoc = await PDFDocument.load(pdfBytes);

    const pages = pdfDoc.getPages();
    const font = await pdfDoc.embedFont(StandardFonts.Helvetica);

    pages.forEach((page, index) => {
        const {width} = page.getSize();
        const pageNumber = index +1;

        page.drawText(`${pageNumber}`, {
            x: width / 2-10, y: 20, size: 12, font: font,
            color: rgb(0,0,0),
        });
    });
    
    const newPdf = await pdfDoc.save();
    fs.writeFileSync(outputPath, newPdf);
}
async function merge_PDF(inputFiles, outputPath) {
    const mergedPDF = await PDFDocument.create();

    for (const filePath of inputFiles){
        const pdfBytes = fs.readFileSync(filePath);
        const pdf = await PDFDocument.load(pdfBytes);
        const copiedPages = await mergedPDF.copyPages(pdf, pdf.getPageIndices());
        copiedPages.forEach(page => mergedPDF.addPage(page));
    }
    const mergedBytes = await mergedPDF.save();
    fs.writeFileSync(outputPath, mergedBytes);
}

async function split_PDF(inputPath, outputFolder) {
    const pdfBytes = fs.readFileSync(inputPath);
    const pdfDoc = await PDFDocument.load(pdfBytes);

    for (let i = 0; i< pdfDoc.getPageCount(); i++){
        const newPDF = await PDFDocument.create();
        const [copiedPage] = await newPDF.copyPages(pdfDoc, [i]);
        newPDF.addPage(copiedPage);
        const pdfData = await newPDF.save();
        fs.writeFileSync(`${outputFolder}/page-${i+1}.pdf`, pdfData);
        }
    }

module.exports = {compress_PDF, merge_PDF, split_PDF};