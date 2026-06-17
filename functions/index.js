const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.checkDangerousVitals = functions.firestore
    .document('VitalSigns/{vitalId}')
    .onCreate(async (snap, context) => {
        try {
            const newVital = snap.data();
            if (!newVital) {
                console.log('Dữ liệu chỉ số sinh tồn bị rỗng.');
                return null;
            }
            
            // Chỉ xử lý nhịp tim
            if (newVital.type === 'heart_rate') {
                const hr = newVital.value;
                
                // LOGIC NGƯỠNG NGUY HIỂM: > 120 bpm hoặc < 50 bpm
                if (hr > 120 || hr < 50) {
                    const patId = newVital.patientId;
                    console.log(`Phát hiện nhịp tim nguy hiểm cho bệnh nhân ${patId}: ${hr} bpm`);
                    
                    // 1. Tìm tất cả các Bác sĩ đang theo dõi bệnh nhân này
                    const accessSnap = await admin.firestore().collection('DoctorAccess')
                        .where('patientId', '==', patId)
                        .where('status', '==', 'active')
                        // Kiểm tra xem còn hạn truy cập không
                        .where('expiryTimestamp', '>', admin.firestore.Timestamp.now())
                        .get();
                        
                    if (accessSnap.empty) {
                        console.log('Không tìm thấy bác sĩ nào đang hoạt động cho bệnh nhân này.');
                        return null;
                    }
                    
                    // 2. Lấy danh sách FCM Token thiết bị của các bác sĩ đó
                    // Trong thực tế, bạn sẽ có collection 'users' lưu FCM Token của bác sĩ
                    const docIds = accessSnap.docs.map(doc => doc.data().doctorId);
                    const tokens = [];
                    
                    for (const docId of docIds) {
                        const userSnap = await admin.firestore().collection('users').doc(docId).get();
                        if (userSnap.exists && userSnap.data().fcmToken) {
                            tokens.push(userSnap.data().fcmToken);
                        }
                    }
                    if (tokens.length === 0) {
                         console.log('Tìm thấy bác sĩ liên kết, nhưng không có FCM token thiết bị nào hợp lệ.');
                         return null;
                    }
                    
                    // 3. Xây dựng message và Gửi Push Notification (FCM HTTP v1 API)
                    const message = {
                        notification: {
                            title: '⚠️ CẢNH BÁO: Nhịp tim bất thường!',
                            body: `Bệnh nhân ID ${patId} vừa ghi nhận nhịp tim nguy hiểm: ${hr} bpm.`
                        },
                        data: { // Dữ liệu đính kèm để mở đúng màn hình
                            patientId: patId,
                            vitalType: 'heart_rate',
                            value: hr.toString(),
                            click_action: 'vn.medisense.app.OPEN_HEALTH_TRACKER' // Đổi từ FLUTTER sang Action của Android
                        },
                        tokens: tokens // Mảng FCM Token
                    };
                    
                    return admin.messaging().sendEachForMulticast(message)
                      .then((response) => {
                        console.log(response.successCount + ' tin nhắn đã được gửi thành công.');
                        if (response.failureCount > 0) {
                            const failedTokens = [];
                            response.responses.forEach((resp, idx) => {
                                if (!resp.success) {
                                    failedTokens.push(tokens[idx]);
                                }
                            });
                            console.log('Danh sách các token bị gửi lỗi:', failedTokens);
                        }
                      })
                      .catch((error) => {
                        console.error('Lỗi khi gửi tin nhắn multicast:', error);
                      });
                }
            }
            return null;
        } catch (error) {
            console.error('Lỗi khi xử lý kiểm tra chỉ số sinh tồn nguy hiểm:', error);
            return null;
        }
    });

exports.onSosRequest = functions.firestore
    .document('SOSRequests/{sosId}')
    .onCreate(async (snap, context) => {
        try {
            const sos = snap.data();
            if (!sos || !sos.patientId) {
                console.log('Thiếu thông tin patientId trong yêu cầu SOS.');
                return null;
            }

            const patId = sos.patientId;
            console.log(`Nhận yêu cầu SOS từ bệnh nhân ${patId}`);

            // 1. Tìm tất cả các Bác sĩ đang theo dõi bệnh nhân này
            const accessSnap = await admin.firestore().collection('DoctorAccess')
                .where('patientId', '==', patId)
                .where('status', '==', 'active')
                .where('expiryTimestamp', '>', admin.firestore.Timestamp.now())
                .get();

            if (accessSnap.empty) {
                console.log('Không tìm thấy bác sĩ nào đang hoạt động để nhận tin nhắn SOS.');
                return null;
            }

            // 2. Lấy danh sách FCM Token thiết bị của các bác sĩ đó
            const docIds = accessSnap.docs.map(doc => doc.data().doctorId);
            const tokens = [];

            for (const docId of docIds) {
                const userSnap = await admin.firestore().collection('users').doc(docId).get();
                if (userSnap.exists && userSnap.data().fcmToken) {
                    tokens.push(userSnap.data().fcmToken);
                }
            }
            if (tokens.length === 0) {
                console.log('Tìm thấy bác sĩ liên kết, nhưng không có FCM token thiết bị nào khả dụng cho SOS.');
                return null;
            }

            // 3. Xây dựng tin nhắn và gửi thông báo khẩn cấp SOS
            const message = {
                notification: {
                    title: '🚨 SOS từ bệnh nhân!',
                    body: `Bệnh nhân ID ${patId} vừa gửi yêu cầu khẩn cấp.`
                },
                data: {
                    patientId: patId,
                    type: 'sos',
                    click_action: 'vn.medisense.app.OPEN_SOS'
                },
                tokens: tokens
            };

            return admin.messaging().sendEachForMulticast(message)
                .then((response) => {
                    console.log(response.successCount + ' tin nhắn SOS đã được gửi thành công.');
                    if (response.failureCount > 0) {
                        const failedTokens = [];
                        response.responses.forEach((resp, idx) => {
                            if (!resp.success) {
                                failedTokens.push(tokens[idx]);
                            }
                        });
                        console.log('Danh sách các token bị gửi lỗi:', failedTokens);
                    }
                })
                .catch((error) => {
                    console.error('Lỗi khi gửi tin nhắn SOS multicast:', error);
                });
        } catch (error) {
            console.error('Lỗi hệ thống khi xử lý yêu cầu SOS:', error);
            return null;
        }
    });
