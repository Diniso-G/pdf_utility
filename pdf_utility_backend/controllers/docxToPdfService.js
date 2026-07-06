const mammoth = require('mammoth');
const puppeteer = require('puppeteer');


async function convertDocxToPDF(docxPath, pdfOutputPath) {
    const { value: html} = await mammoth.convertToHtml({ path: docxPath});

    const styledHtml = `
    <html> 
        <head> 
            <meta charset="utf-8"/> 
            <style> 
                body {
                    font-family: Helvetica, Arial, sans-serif; 
                    font-size: 12pt; 
                    line-height: 1.5; 
                    margin: 40px;
                } 
                h1, h2, h3 {
                    font-weight: bold;
                } 
                img {
                    max-width: 100%;
                } 
            </style> 
        </head> 
        <body>${html}</body>
    </html> `;
                     
    const browser = await puppeteer.launch({arg: ['--no-sandbox']});
    try{
        const page = await browser.newPage();
        await page.setContent(styledHtml, { waitUntil: 'networkidle0'});

        await page.pdf({ path: pdfOutputPath, format: 'A4', margin: {top: '20mm', bottom: '20mm', left: '15mm', right: '15mm'},});
    }
    finally {
        await browser.close();
    }
}

module.exports = {convertDocxToPDF};


