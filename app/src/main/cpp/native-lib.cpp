#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/dnn.hpp>
#include <android/log.h>

#include <fstream>

#define  LOG_TAG    "Termik"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

using namespace cv;
using namespace cv::dnn;

static char obbMountPath[256];

/* Find best class for the blob (i. e. class with maximal probability) */
void getMaxClass(dnn::Blob &probBlob, int *classId, double *classProb)
{
    Mat probMat = probBlob.matRefConst().reshape(1, 1); //reshape the blob to 1x1000 matrix
    Point classNumber;

    minMaxLoc(probMat, NULL, classProb, NULL, &classNumber);
    *classId = classNumber.x;
}


std::vector<String> readClassNames(const char *filename)
{
    std::vector<String> classNames;

    std::ifstream fp(filename);
    if (!fp.is_open())
    {
        std::cerr << "File with classes labels not found: " << filename << std::endl;
        exit(-1);
    }

    std::string name;
    while (!fp.eof())
    {
        std::getline(fp, name);
        if (name.length())
            classNames.push_back( name );
    }

    fp.close();
    return classNames;
}

void incept(Mat img) {
	String modelFile;
	String labelsFile;
	modelFile += obbMountPath;
	labelsFile += obbMountPath;
	modelFile += "/tensorflow_inception_graph.pb";
	labelsFile += "/imagenet_comp_graph_label_strings.txt";

	Ptr<dnn::Importer> importer;
	try                                     //Try to import TensorFlow AlexNet model
    {
        importer = dnn::createTensorflowImporter(modelFile);
    }
    catch (const cv::Exception &err)        //Importer can throw errors, we will catch them
    {
		LOGI("Import Exception");
        std::cerr << err.msg << std::endl;
    }
	if (!importer)
    {
		LOGI("Load error");
        std::cerr << "Can't load network by using the mode file: " << std::endl;
        std::cerr << modelFile << std::endl;
        exit(-1);
    }
	 //! [Initialize network]
    dnn::Net net;
    importer->populateNet(net);
    importer.release();

	cv::Size inputImgSize = cv::Size(224, 224);
    resize(img, img, inputImgSize);  
	cv::cvtColor(img, img, cv::COLOR_RGBA2RGB);


	dnn::Blob inputBlob = dnn::Blob::fromImages(img);   //Convert Mat to dnn::Blob image batch
    //! [Prepare blob]

    //! [Set input blob]

	String inBlobName = ".input";
    net.setBlob(inBlobName, inputBlob);        //set the network input
    //! [Set input blob]

    cv::TickMeter tm;
    tm.start();

    //! [Make forward pass]
    net.forward();                          //compute output
    //! [Make forward pass]

    tm.stop();

    //! [Gather output]
	String outBlobName = "softmax2";
    dnn::Blob prob = net.getBlob(outBlobName);   //gather output of "prob" layer

    Mat& result = prob.matRef();

    BlobShape shape = prob.shape();

	if (!labelsFile.empty()) {
        std::vector<String> classNames = readClassNames(labelsFile.c_str());

        int classId;
        double classProb;
        getMaxClass(prob, &classId, &classProb);//find the best class

        //! [Print results]
        std::cout << "Best class: #" << classId << " '" << classNames.at(classId) << "'" << std::endl;
        std::cout << "Probability: " << classProb * 100 << "%" << std::endl;
		LOGI("Best class: %s", classNames.at(classId).c_str());
		LOGI("Probability: %f", classProb);
    }
}



extern "C"
{
void JNICALL Java_uk_co_mishurov_termik_MainActivity_salt(
	JNIEnv *env, jobject instance, jlong image) {
    Mat &img = *(Mat *) image;
	if(obbMountPath[0] != '\0') {
		incept(img.clone());
	}
}

void JNICALL Java_uk_co_mishurov_termik_MainActivity_setdir(
	JNIEnv *env, jobject instance, jstring path) {
	strcpy(obbMountPath, (char*)env->GetStringUTFChars(path, NULL));
}

}

