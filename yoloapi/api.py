from flask import Flask, request, jsonify
from ultralytics import YOLO
import os
import uuid
from werkzeug.utils import secure_filename
import base64
from PIL import Image
import io

app = Flask(__name__)

# 配置文件上传
UPLOAD_FOLDER = r"./uploads"
RESULT_FOLDER = r"./results"
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'bmp', 'tiff'}

# 创建必要的文件夹
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(RESULT_FOLDER, exist_ok=True)

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['RESULT_FOLDER'] = RESULT_FOLDER
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 限制上传文件大小为16MB

# 加载YOLO模型
try:
    model = YOLO(r"label.pt")
    print("模型加载成功！")
except Exception as e:
    print(f"模型加载失败: {e}")
    model = None


def allowed_file(filename):
    """检查文件扩展名是否允许"""
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def process_image(image_path):
    """处理图像并进行YOLO检测"""
    results = model.predict(
        source=image_path,
        show=False,
        save=True,
        project=app.config['RESULT_FOLDER'],
        name='detections',
        conf=0.65
    )

    detections = []
    for result in results:
        boxes = result.boxes

        if boxes is not None and len(boxes) > 0:
            class_ids = boxes.cls.tolist()
            confidences = boxes.conf.tolist()
            boxes_xyxy = boxes.xyxy.tolist()  # 获取边界框坐标

            class_names = [result.names[int(id)] for id in class_ids]

            for i, (name, conf, bbox) in enumerate(zip(class_names, confidences, boxes_xyxy)):
                detection = {
                    'target_id': i + 1,
                    'class_name': name,
                    'confidence': round(conf, 3),
                    'bbox': {
                        'x1': round(bbox[0], 2),
                        'y1': round(bbox[1], 2),
                        'x2': round(bbox[2], 2),
                        'y2': round(bbox[3], 2)
                    }
                }
                detections.append(detection)

    return detections


@app.route('/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    if model:
        return jsonify({'status': 'healthy', 'model_loaded': True})
    else:
        return jsonify({'status': 'unhealthy', 'model_loaded': False}), 503


@app.route('/predict/image', methods=['POST'])
def predict_image():
    """预测接口 - 上传图片文件"""
    if model is None:
        return jsonify({'error': '模型未加载'}), 500

    # 检查是否有文件上传
    if 'image' not in request.files:
        return jsonify({'error': '没有上传图片文件'}), 400

    file = request.files['image']

    if file.filename == '':
        return jsonify({'error': '未选择文件'}), 400

    if file and allowed_file(file.filename):
        # 生成唯一文件名
        filename = str(uuid.uuid4()) + '_' + secure_filename(file.filename)
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        file.save(filepath)

        try:
            # 处理图片
            detections = process_image(filepath)

            # 获取保存的结果图片路径
            result_image_path = os.path.join(app.config['RESULT_FOLDER'], 'detections', filename)

            response = {
                'success': True,
                'filename': filename,
                'detections_count': len(detections),
                'detections': detections
            }

            # 如果检测到目标，返回结果图片的base64编码
            if len(detections) > 0 and os.path.exists(result_image_path):
                with open(result_image_path, 'rb') as img_file:
                    img_base64 = base64.b64encode(img_file.read()).decode('utf-8')
                    response['result_image_base64'] = img_base64

            return jsonify(response)

        except Exception as e:
            return jsonify({'error': f'处理图片时出错: {str(e)}'}), 500
        finally:
            # 清理上传的临时文件
            if os.path.exists(filepath):
                os.remove(filepath)

    return jsonify({'error': '不支持的文件格式'}), 400


@app.route('/predict/base64', methods=['POST'])
def predict_base64():
    """预测接口 - 接收base64编码的图片"""
    if model is None:
        return jsonify({'error': '模型未加载'}), 500

    data = request.get_json()

    if not data or 'image_base64' not in data:
        return jsonify({'error': '请提供base64编码的图片'}), 400

    try:
        # 解码base64图片
        image_data = base64.b64decode(data['image_base64'])
        image = Image.open(io.BytesIO(image_data))

        # 保存临时文件
        filename = str(uuid.uuid4()) + '.jpg'
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        image.save(filepath)

        # 处理图片
        detections = process_image(filepath)

        response = {
            'success': True,
            'detections_count': len(detections),
            'detections': detections
        }

        return jsonify(response)

    except Exception as e:
        return jsonify({'error': f'处理base64图片时出错: {str(e)}'}), 500
    finally:
        # 清理临时文件
        if os.path.exists(filepath):
            os.remove(filepath)


@app.route('/predict/url', methods=['POST'])
def predict_url():
    """预测接口 - 接收图片URL"""
    if model is None:
        return jsonify({'error': '模型未加载'}), 500

    data = request.get_json()

    if not data or 'image_url' not in data:
        return jsonify({'error': '请提供图片URL'}), 400

    try:
        import requests
        from urllib.parse import urlparse

        image_url = data['image_url']

        # 下载图片
        response = requests.get(image_url, timeout=10)
        response.raise_for_status()

        # 从URL获取文件名
        parsed_url = urlparse(image_url)
        original_filename = os.path.basename(parsed_url.path)
        if not original_filename or '.' not in original_filename:
            original_filename = 'image.jpg'

        # 生成唯一文件名
        filename = str(uuid.uuid4()) + '_' + secure_filename(original_filename)
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)

        with open(filepath, 'wb') as f:
            f.write(response.content)

        # 处理图片
        detections = process_image(filepath)

        response_data = {
            'success': True,
            'detections_count': len(detections),
            'detections': detections
        }

        return jsonify(response_data)

    except requests.exceptions.RequestException as e:
        return jsonify({'error': f'下载图片失败: {str(e)}'}), 400
    except Exception as e:
        return jsonify({'error': f'处理图片时出错: {str(e)}'}), 500
    finally:
        # 清理临时文件
        if os.path.exists(filepath):
            os.remove(filepath)


@app.errorhandler(413)
def too_large(e):
    return jsonify({'error': '文件太大'}), 413


@app.errorhandler(404)
def not_found(e):
    return jsonify({'error': '接口不存在'}), 404


@app.errorhandler(500)
def internal_error(e):
    return jsonify({'error': '服务器内部错误'}), 500


if __name__ == '__main__':
    # 启动Flask应用
    app.run(host='127.0.0.1', port=5000, debug=False)