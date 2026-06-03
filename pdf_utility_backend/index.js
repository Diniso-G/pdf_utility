const express = require('express');
const cors = require('cors');
const path = require('path'); //NOT SURE ABOUT THIS
const pdfRoutes = require('./routes/pdf.routes');

const app = express();

app.use(cors());
app.use(express.json());
app.use('/api/pdf', pdfRoutes);

app.use('/uploads', express.static(path.join(__dirname,'uploads')));

app.get('/', (req, res) => {
    res.json({ status: 'Server is running. Testing 3.'});
});

const PRIVATE_IP = '0.0.0.0' ; // COME 
const PORT = 3001;
app.listen(PORT, PRIVATE_IP,() => {
    console.log(`Server running on port ${PRIVATE_IP} and ${PORT}`);
});

setInterval(() => {
    console.log(`[${new Date().toLocaleDateString()}] Server still alive....`);
}, 10000); //every 10 seconds

