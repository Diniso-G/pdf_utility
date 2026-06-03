# PDF Utility Tool
A mobile focused Android PDF Utility application that allows users to upload, process and view PDF files. The app supports splitting, merging and adding page numbers to PDFs, demonstrating full-stak development and SDLC best practices.

## Features
- Upload one or multiple PDF files
- Merge multiple PDFs into one
- Split PDF into individual pages
- Add page numbers
- View PDFs 

## System Architecture
### Client Server(Mobile First) Architecture
- Android Client: Handles UI, file seelction and viewing
- Backend Server: Handles file uploads, validation and processing
- PDF Processing Layer: Performs PDF operations using dedicated libraries
- Temporary Storage: Stores files during processing

## Technology Stack
### Android Client
- Java
- Android Studio
- XML
- Android Storage Access Framework
- PDFBox-Android

### Backend Server
- Node.js
- Express.js
- Multer (file uploads)
- Pdf-lib
- Axios

## API Endpoints
- POST: /api/pdf/upload Upload PDF files
- -POST: /api/pdf/process Process PDFs
- GET: /api/pdf/open

## Security
- File type and size validation
- Temporary file clean up after processing
- Private network-only backed during development
- HTTPS recommended for deployment

## Testing
- HTML file to test workflow
- port testing for PC and Device
- System testing of full add after development

## Limitations
- Android only support for current version
- No cloud storage

## Author
Diniso Gwabeni
Portfolio Project= PDF Utility Tool
Version 1
