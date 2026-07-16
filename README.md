# PDF Utility Tool
A mobile focused Android PDF Utility application that allows users to select, process and view PDF files. The app uses a **hybrid architecture**, core PDF operations run entirely on device for speed and offline reliability. While Word-to-Pdf conversion is handled by a backend server. It is a design decision that demonstrates full-stack client-server workflow where it actually adds value, rather than putting everything on one side by defualt.

## Features
- Upload/select one or multiple PDF files and select Word document
- Merge multiple PDFs into one(done on client side)
- Split PDF into individual pages(done on client side)
- Add page numbers(done on client 
side)
- Convert Word document to PDF(done on server side)
- View PDFs 

## System Architecture
**Hybrid Client-Server Architeture**

- **Android Client**: Handles UI, file seelction and viewing. Performs merge. split, and page-numbering directly on device using PDFBox-Android. All PDF processing runs on a backgroudn thread with a progress dialog, so the UI nvere freezes.
- **Backend Server**: Handles Word-to-PDF conversion. The only operations that can't be done on the client side. Uploaded and converted files are tracked in memory only for the life of a single converion job.
- **PDF Processing Layer**: Performs PDF operations using dedicated libraries via PDFBox-Android. Server side, `mammoth` extracts the docx content as HTML and `puppeteer` renders that HTML to PDF- this gives much closer to Word layout accuracy than a hand rolled renderer would.
- **Temporary Storage**: The server briefly writes uploaded/converted files to local disk during a conversion job, then deletes them. No persident database, no cloud storage.

## Technology Stack
### Android Client
- Java
- Android Studio
- XML
- Android Storage Access Framework
- PDFBox-Android (for on device operations)
- OkHttp (network calls, used only for Word to Pdf)
- org.json (parsing the server responses)

### Backend Server
- Node.js
- Express.js
- Multer (file uploads)
- mammoth (docx to html conversion)
- puppeteer (html to pdf conversion)

## API Endpoints
- `POST: /api/pdf/upload` Upload PDF files - multipart field `file`, returns `{fileID}`
- `POST: /api/pdf/process `Process PDFs - JSON body `{fileID, operation: "docxToPDF"}`, returns {resultID}`
- `GET: /api/pdf/open/:resultID` - streams back the converted PDF, then delets the temporary copy


## Running the backend
```
cd pdf_utility_backend
npm install
npm start
```
You should see `Server running on port 3000` in the terminal

## Connecting the Android app to the backend

`BASE_URL` in `MainActivity.java` needs to point at wherever the backend is actually reachable from the device running the app:


| Setup | BASE_URL |
|---|---|
|Android **emulator** | `http://10.0.2.2:3000` |
| Physical device, same wifi as the PC(device with the server) | `http://<PC's LAN IP>:300` (find via `ipconfig`) |
| Physical device via USB (most reliable) | `http://localhost:3000`, plus run `adb reverse tcp:3000` first |
|||

The USB method was the one that ended up working reliably during development, since the test PC and phone were on different subnets even while "on same wifi."
`adb reveser` forwards the phone's `localhost:3000` straight to the PC's `localhost:3000` over the USB cable, sidestepping wifi/subnet/firewall issues entirely.
Note: this forward has to be re run any time the USB cable is reconnected or adb restarts.


## Limitations
- Word-To-PDF requires network access to reach the backend; it does not work offline(by design - this is the intentional hybrid trade-off)
- Larger or more complex `.docx` files can take a while to convert (Puppeteer has to launch a full headless browser per request). the clinet's OkHttp timeouts are set generously to accomodate this, but very large documents may still need further optimization
- Conversion quality depends on `mammoth`'s HTML extraction- simple document (Paragraphs, headings, bold/italic, basic lists) convert very close to the original. Complex layouts (tables, tracked changes, embedded objects, multicolumn text may not render indentically to Word)
- Android only support for current version
- No cloud storage, no database- the backend is intentionally stateless

## Security
- File type and size validation on upload (20MB limit, `.docx`/`.doc` only)
- Temporary file clean up after processing by deleting immediately after each processing step
- Private network-backend intended for local network or USB-forwarded use during development
- HTTPS recommended for any deployment beyond local development or on live demo

## Testing
- HTML file to test workflow
- port testing for PC and Device
- System testing of full add after development


## Author
Diniso Gwabeni
