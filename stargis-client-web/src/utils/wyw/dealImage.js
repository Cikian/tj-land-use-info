// 获取base64图片大小，返回MB数字
function calSize(base64url) {
  let str = base64url.replace('data:image/png;base64,', '');
  const equalIndex = str.indexOf('=');
  if(str.indexOf('=') > 0) {
    str = str.substring(0, equalIndex);
  }
  const strLength = str.length;
  const fileLength = strLength - (strLength / 8) * 2;
  // 返回单位为MB的大小
  return  (fileLength / (1024 * 1024)).toFixed(2);
}

// 通过canvas压缩base64图片
function dealImage(base64, callback, w) {
  w = w || 1000;
  const newImage = new Image();
  const quality = 0.8;    // 压缩系数0-1之间
  newImage.src = base64;
  newImage.setAttribute("crossOrigin", 'Anonymous');    // url为外域时需要
  let imgWidth;
  let imgHeight;
  newImage.onload = function() {
    // @ts-ignore
    imgWidth = this.width;
    // @ts-ignore
    imgHeight = this.height;
    const canvas = document.createElement("canvas");
    const ctx = canvas.getContext("2d");
    if (Math.max(imgWidth, imgHeight) > w) {
      if (imgWidth > imgHeight) {
        canvas.width = w;
        canvas.height = w * imgHeight / imgWidth;
      } else {
        canvas.height = w;
        canvas.width = w * imgWidth / imgHeight;
      }
    } else {
      canvas.width = imgWidth;
      canvas.height = imgHeight;
    }
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    // @ts-ignore
    ctx.drawImage(this, 0, 0, canvas.width, canvas.height);
    const newBase64 = canvas.toDataURL("image/jpeg", quality);
    callback(newBase64);
  }
}

  function base64ImgtoFile(dataurl, filename = "file") {
    let arr = dataurl.split(",");

    let mime = arr[0].match(/:(.*?);/)[1];

    let suffix = mime.split("/")[1];

    let bstr = atob(arr[1]);

    let n = bstr.length;

    let u8arr = new Uint8Array(n);

    while (n--) {
      u8arr[n] = bstr.charCodeAt(n);
    }

    return new File([u8arr], `${filename}.${suffix}`, {
      type: mime,
    });
  }
export {
  calSize,
  dealImage,
  base64ImgtoFile
}
