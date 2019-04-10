package ai.newmap.nluLayer

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import scala.io.Source
import java.io.PrintWriter
import java.nio.file.{Paths, Files}

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileOutputStream
import org.apache.commons.io.IOUtils
import scala.collection.JavaConversions._
import scala.collection.immutable.ListMap

import ai.newmap.environment.envConstant
import ai.newmap.nluLayer.actionProcessor._

object nluInterpreter {
	val BUCKET_NAME = envConstant.BUCKET_NAME
	val AWS_ACCESS_KEY = envConstant.AWS_ACCESS_KEY
	val AWS_SECRET_KEY = envConstant.AWS_SECRET_KEY

	val S3_ModelFileName_Prefix = "Model/"
	val S3_CacheFileName_Prefix = "CACHE/"

	var ActionMap:ListMap[String, String] = ListMap.empty[String,String]

	def loadActionModel() = {
		val awsCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
  		val amazonS3Client = new AmazonS3Client(awsCredentials)

  		/* // get model file names
  		val modelFileNames = amazonS3Client.listObjects(BUCKET_NAME, S3_ModelFileName_Prefix).getObjectSummaries()
  		for(str <- modelFileNames.toList){
  			println("******"+str.getKey()+"******")
  		}
  		*/

  		val actionModFileName = "action_model.txt"
  		val actionModObj = amazonS3Client.getObject(BUCKET_NAME, S3_ModelFileName_Prefix+actionModFileName)
  		val actionModReader = new BufferedReader(new InputStreamReader(actionModObj.getObjectContent()))
  		var actionModLine = actionModReader.readLine
  		while(actionModLine != null){
  			val cont = actionModLine.split(",")
  			ActionMap += (cont(0) -> cont(1))
  			actionModLine = actionModReader.readLine
  		}

  		println("*** action map ***")
  		ActionMap.forEach{case (key, value) => println (key + "-->" + value)}
  	}

	def nluInterp(chanName:String, userName: String, code: String): String = {
		val awsCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
  		val amazonS3Client = new AmazonS3Client(awsCredentials)

  		loadActionModel

		// read from nlu cache
		var msg = ""
		var cache_cont = ""
		val nluCacheFileName = chanName+"_"+userName+"_nlu_cache.txt"
		if(amazonS3Client.doesObjectExist(BUCKET_NAME, S3_CacheFileName_Prefix+nluCacheFileName)){
			val nluCacheObj = amazonS3Client.getObject(BUCKET_NAME, S3_CacheFileName_Prefix+nluCacheFileName)
			val nluCacheReader = new BufferedReader(new InputStreamReader(nluCacheObj.getObjectContent()))
			val nluCacheLine = nluCacheReader.readLine
			msg += nluCacheLine+" "
		}
		msg += code
		// append msg

		// interpete
		println("*** "+msg+" ***")
		val cont = msg.toLowerCase().split("\\s+")
		var actionType: String = ""
		var gotActionType: Boolean = false
		// check action kind
		for(tok <- cont if !gotActionType){
			//println(tok)
			if(ActionMap.contains(tok)){
				actionType = ActionMap(tok)
				println("*** tok: "+tok+", action type: "+actionType+" ***")
				gotActionType = true
			}
		}

		if(!gotActionType){

			return "*Didn't recognize action in this message, please tell me exactly what you want to do*"
					// TODO: add recommend actions as response
		}

		// process action
		actionType match {
			case "create" => {
				return processCreateAct(msg, nluCacheFileName)
			}
			case "accessEnv" => {
				return processAccessEnvAct(msg, nluCacheFileName)
			}
			case "print" => {
				return processPrintAct(msg, nluCacheFileName)
			}
			case "comment" => {
				return argParser.parseCommentEnvArg(msg, nluCacheFileName)
			}
			case "commit" => {
				return argParser.parseCommitArg(msg, nluCacheFileName)
			}
			case "reset" => {
				return processResetAct(msg, nluCacheFileName)
			}
			case "append" => {
				return argParser.parseAppendArg(msg, nluCacheFileName)
			}
			case _ => {
				return "*** Fail because of logic error. "+actionType+" does not exist ***"
			}
		}

	}
	
	def writeToCache(str: String, cacheFileName: String) = {
		val awsCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
  		val amazonS3Client = new AmazonS3Client(awsCredentials)

		val cacheFile = new File(cacheFileName)
		val cacheFileWriter = new FileWriter(cacheFile, false)
		val cacheBufferedWriter = new BufferedWriter(cacheFileWriter)
		cacheBufferedWriter.write(str)
		cacheBufferedWriter.close()
		amazonS3Client.putObject(BUCKET_NAME, S3_CacheFileName_Prefix+cacheFileName, cacheFile)
	}
}