const express = require('express');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const {convertDocxToPDF} = require('../controllers/docxToPdfService');

const router = express.Router();

const UPLOAD_DIR = path.join(__dirname, '..', 'tmp', 'uploads');
const PROCESSED_DIR = path.join(__dirname, '..', 'tmp', 'processed');

fs.mkdirSync(UPLOAD_DIR, { recursive: true});
fs.mkdirSync(PROCESSED_DIR, { recursive: true});

const uploadedFiles = new Map();
const processedFiles = new Map();

/*

const storage = multer.diskStorage({
    destination: 'uploads/', filename: (req , file, cb) => {
        cb(null, Date.now() + '-' + file.originalname);
    }
});

const upload = multer({
    storage, fileFilter: (req, file, cb) => {
        if (file.mimetype !== 'application/pdf') {
            return cb(new Error('Only PDFs allowed'));
        }
        cb(null, true);
    }
});*/

const upload = multer({ dest: UPLOAD_DIR, limits: { fileSize: 20* 1024* 1024}});

const Allowed_MIME = new Set([
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/msword',
]);

router.post('/upload', upload.single('file'), (req, res) => {
    if (!req.file){
        return res.status(400).json({ error: 'No file uploaded'});
    }
    if (!Allowed_MIME.has(req.file.mimetype)) {
        fs.unlink(req.file.path, () => {});
        return res.status(415).json({ error: 'Only .docx/ .doc files are supported'});
    }

    const fileID = crypto.randomUUID();
    uploadedFiles.set(fileID, req.file.path);
    res.json({ fileID});
    /*res.json({
        message: 'PDF uploaded successfully', file: req.file.filename
    });*/
});

//const {compress_PDF} = require('../utils/pdfProcessor');

router.post('/process', async (req, res) => {
    const {fileID, operation} = req.body || {};
    const inputPath = uploadedFiles.get(fileID);

    if(!inputPath || !fs.existsSync(inputPath)) {
        return res.status(404).json({error: 'Unknown or expire fileID'});
    }


    if(operation !== 'docxToPDF') {
         return res.status(400).json({error: `Unsupported operation: ${operation}`});
    }

    try {
        const resultID = crypto.randomUUID();
        const outputPath = path.join(PROCESSED_DIR, `${resultID}.pdf`);

        await convertDocxToPDF(inputPath, outputPath);
        processedFiles.set(resultID, outputPath);

        fs.unlink(inputPath, () => {});
        uploadedFiles.delete(fileID);

        res.json({ resultID});
    }
    catch (err) {
        console.error('Processing error: ', err);
        res.status(500).json({error: 'Processing failed', details: err.message});
    }
    /*
    try {
        const {filename} = req.body;
        if (!filename) return res.status(400).json({error: 'Filename is required'});

        const inputPath = `uploads/${filename}`;
        const outputPath = `uploads/processed-${filename}`;

        //const {compress_PDF} = require('../utils/pdfProcessor');
        await compress_PDF(inputPath, outputPath);

        res.json({
            message: 'PDF Processed Successfully', file: `processed-${filename}`
        });
    } catch (err){
        res.status(500).json({ error: err.message});
    }*/
});

/*
const {merge_PDF, split_PDF} = require('../utils/pdfProcessor');

router.post('/merge', async (req, res) => {
    try{
        const {files} = req.body;
        const inputPath = files.map(f => `uploads/${f}`);
        const outputPath = `uploads/merged-${Date.now()}.pdf`;
        await merge_PDF(inputPath, outputPath);
        res.json({message: 'Merged successfully', file: outputPath.split('/').pop()});
    } catch (err){
        res.status(500).json({error: err.message});
    }
});
router.post('/split', async (req, res) => {
    try{
        const {filename} = req.body;
        if (!filename) return res.status(400).json({error: 'Filename required'});
        
        const inputPath = `uploads/${filename}`;
        if (!fs.existsSync(inputPath)) return res.status(404).json({error: 'File not found'});

        const outputFolder = `uploads/split`;
        if (!fs.existsSync(outputFolder)) fs.mkdirSync(outputFolder, {recursive: true});
        await split_PDF(inputPath, outputFolder);

        const splitFiles = fs.readdirSync(outputFolder).filter(f => f.endsWith('.pdf'));
        const filesWithPath = splitFiles.map(f => `split/${f}`);
        res.json({message: 'Split successfully', files: filesWithPath});
    } catch (err){
        res.status(500).json({error: err.message});
    }
});*/

router.get('/open/:resultID', (req, res) => {
    const outputPath = processedFiles.get(req.params.resultID);

    if (!outputPath || !fs.existsSync(outputPath)) {
        return res.status(404).json({error: 'Unknown or expired resultID'});
    }
    res.setHeader('Content-Type', 'application/pdf');
    res.sendFile(outputPath, (err) => {
        if (!err){
            fs.unlink(outputPath, () => {});
            processedFiles.delete(req.params.resultID);
        }
    });
});

module.exports = router;