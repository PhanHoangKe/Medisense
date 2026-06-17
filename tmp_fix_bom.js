const fs = require('fs');
const path = require('path');

function walkDir(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(function(file) {
        file = path.join(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) { 
            results = results.concat(walkDir(file));
        } else {
            if (file.endsWith('.java') || file.endsWith('.xml')) {
                results.push(file);
            }
        }
    });
    return results;
}

const files = walkDir('D:\\MediSense\\app\\src\\main\\java');

let fixedCount = 0;
files.forEach(file => {
    const content = fs.readFileSync(file);
    if (content.length >= 3 && content[0] === 0xEF && content[1] === 0xBB && content[2] === 0xBF) {
        // has BOM
        const str = content.toString('utf8', 3); // decode skipping first 3 bytes
        fs.writeFileSync(file, str, 'utf8');
        console.log('Fixed BOM for:', file);
        fixedCount++;
    }
});

console.log('Total files fixed:', fixedCount);
