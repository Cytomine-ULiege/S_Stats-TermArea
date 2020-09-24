/*
* Copyright (c) 2009-2020. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.client.Cytomine
import be.cytomine.client.models.*
import be.cytomine.client.collections.*
import java.nio.file.Files

// Get command line parameters
String host = args[0]
String publicKey = args[1]
String privateKey = args[2]
Long idProject = Long.parseLong(args[3])
Long idSoftware = Long.parseLong(args[4])
String[] idTerms = args[5].split(',')
String[] idImages = args[6].split(',')
Boolean reviewedOnly = Boolean.parseBoolean(args.length > 7 ? args[7] : 'false')


// Establish connection with Cytomine server
Cytomine.connection(host, publicKey, privateKey)

User currentUser = User.getCurrent()
def runByUI = false
Job job
if (currentUser.isHuman()) {
    // If user connects as a human (CLI execution)
    job = new Job(idSoftware, idProject).save()
    def userJob = new User().fetch(job.get("userJob"))
    Cytomine.connection(host, userJob.get("publicKey"), userJob.get("privateKey"))
}
else {
    // If the user executes the job through the Cytomine interface
    job = new Job().fetch(currentUser.get("job"))
    runByUI = true
}

// Publish parameters
if (!runByUI) {
    SoftwareParameterCollection softwareParameters = SoftwareParameterCollection.fetchBySoftware(idSoftware)
    softwareParameters.each({
        def value = null
        if (it.name == 'cytomine_id_terms')
            value = idTerms
        else if (it.name == 'cytomine_id_images')
            value = idImages
        else if (it.name == 'cytomine_reviewed_only')
            value = reviewedOnly

        if (value)
            new JobParameter(job.getId(), it.id, value.toString()).save()
    })
}

try {
    Cytomine cytomine = Cytomine.getInstance()
    // Start job
    job.changeStatus(Job.JobStatus.RUNNING, 0, "Collect data")
    def terms = idTerms.collect { id ->
        return new Term().fetch(Long.parseLong(id))
    }

    def images = idImages.collect { id ->
        return new ImageInstance().fetch(Long.parseLong(id))
    }

    def datas = []
    terms.each { term ->
        images.each { image ->
            def filters = [:]
            if (reviewedOnly) filters.put("reviewed", "true")
            filters.put("term", term.getId().toString())
            filters.put("image", image.getId().toString())
            filters.put("showGIS", "true")
            filters.put("showMeta", "true")
            filters.put("showTerm", "true")
            AnnotationCollection annotations = cytomine.getAnnotations(filters)

            for (int i = 0; i < annotations.size(); i++) {
                datas << [
                        image: image,
                        term: term,
                        created: new Date(annotations.get(i).get('created').longValue()),
                        area: annotations.get(i).getDbl('area')
                ]
            }
        }
    }

    job.changeStatus(Job.JobStatus.RUNNING, 20, "Compute statistics")
    def file = writeCSVReport(terms, images, datas)

    job.changeStatus(Job.JobStatus.RUNNING, 90, "Upload report")
    def jobData = new JobData(job, "output", "stats.csv").save()
    jobData.uploadJobData(file)

    job.changeStatus(Job.JobStatus.SUCCESS, 100, "Finished")
}
catch(Exception e) {
    job.changeStatus(Job.JobStatus.FAILED, 100, "Error: ${e.toString()}")
}


static def writeCSVReport(terms, images, datas) {
    def csv = new File('output.csv')
    csv.write ''
    csv << "Area statistics,\n"
    csv << "Image,"
    for (int i = 0; i < terms.size(); i++) {
        csv << "${terms[i].getStr('name')},"
    }
    csv << "Total,\n"

    for (int i = 0; i < images.size(); i++) {
        csv << "${images[i].getStr('instanceFilename')},"
        def datasByImage = datas.findAll { it.image == images[i] }
        for (int j = 0; j < terms.size(); j++) {
            def totArea = datasByImage.findAll { it.term == terms[j] }.collect { it.area }.sum() ?: 0.0d
            csv << "${totArea.round()},"
        }
        def totArea = datasByImage.collect { it.area }.sum() ?: 0.0d
        csv << "${totArea.round()},\n"
    }

    csv << "Total,"
    for (int i = 0; i < terms.size(); i++) {
        def totArea = datas.findAll { it.term == terms[i] }.collect { it.area }.sum() ?: 0.0d
        csv << totArea.round() + ","
    }
    def totArea = datas.collect { it.area }.sum() ?: 0.0d
    csv << totArea.round() + ",\n"

    for (int i = 0; i < 2; i++) {
        csv << ",,,\n"
    }

    csv << "Number statistics,\n"
    csv << "Image,"
    for (int i = 0; i < terms.size(); i++) {
        csv << terms[i].getStr('name') + ","
    }
    csv << "Total,\n"

    def totNumber
    for (int i = 0; i < images.size(); i++) {
        csv << images[i].getStr('instanceFilename') + ","
        def datasByImage = datas.findAll { it.image == images[i] }
        for (int j = 0; j < terms.size(); j++) {
            totNumber = datasByImage.findAll { it.term == terms[j] }.size()
            csv << totNumber + ","
        }
        totNumber = datasByImage.size()
        csv << totNumber + ",\n"
    }

    csv << "Total,"
    for (int i = 0; i < terms.size(); i++) {
        totNumber = datas.findAll { it.term == terms[i] }.size()
        csv << totNumber + ","
    }
    totNumber = datas.size()
    csv << totNumber + ",\n"

    for (int i = 0; i < 2; i++) {
        csv << ",,,\n"
    }

    csv << "Data ratio,\n"
    csv << "Image,"
    for (int i = 0; i < terms.size(); i++) {
        csv << terms[i].getStr('name') + ","
    }
    csv << "Total,\n"

    for (int i = 0; i < images.size(); i++) {
        csv << images[i].getStr('instanceFilename') + ","
        def datasByImage = datas.findAll { it.image == images[i] }
        def totAreaByImage = datasByImage.collect { it.area }.sum() ?: 0
        for (int j = 0; j < terms.size(); j++) {
            def termArea = datasByImage.findAll { it.term == terms[j] }.collect { it.area }.sum() ?: 0.0d
            def ratio = totAreaByImage == 0.0 ? 0.0d : (termArea / totAreaByImage)
            csv << ratio.round(6) + ","
        }
        def ratio = 1
        csv << ratio + ",\n"
    }

    for (int i = 0; i < 2; i++) {
        csv << ",,,\n"
    }

    csv << "Details,\n"
    for (int i = 0; i < images.size(); i++) {
        csv << "*****************,\n"
        csv << "Image " + (i + 1) + ",\n"
        csv << images[i].getStr('instanceFilename') + ",\n"
        csv << "*****************,\n"

        for (int j = 0; j < terms.size(); j++) {
            if (j > 0) {
                for (int k = 0; k < 2; k++) {
                    csv << ",,,\n"
                }
            }

            csv << "#####,\n"
            csv << terms[j].getStr('name') + ",\n"
            csv << "Created,Area,\n"
            datas.findAll { it.term == terms[j] && it.image == images[i] }.each {
                csv << it.created.format('yyyy.MM.dd HH:mm:ss') + "," + it.area.round(1) + ",\n"
            }
            csv << ",,,\n"

            def annots = datas.findAll { it.term == terms[j] && it.image == images[i] }
            def nbAnnots = annots.size()
            totArea = annots.collect { it.area }.sum() ?: 0.0d
            def meanArea = nbAnnots == 0.0 ? 0.0d : (totArea / nbAnnots)

            csv << "Number of annotations," + nbAnnots + ",\n"
            csv << "Average area," + meanArea.round() + ",\n"
            csv << "Total area," + totArea + ",\n"
        }

        for (int k = 0; k < 2; k++) {
            csv << ",,,\n"
        }
    }
    return csv
}
