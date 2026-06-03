const express = require('express');
const multer = require('multer');
const fs = require('fs');

const router = express.Router();

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
});

router.post('/upload', upload.single('pdf'), (req, res) => {
    res.json({
        message: 'PDF uploaded successfully', file: req.file.filename
    });
});

const {compress_PDF} = require('../utils/pdfProcessor');

router.post('/process', async (req, res) => {
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
    }
});

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
});

module.exports = router;