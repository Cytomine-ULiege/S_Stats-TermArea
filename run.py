# * Copyright (c) 2020. Authors: see NOTICE file.
# *
# * Licensed under the Apache License, Version 2.0 (the "License");
# * you may not use this file except in compliance with the License.
# * You may obtain a copy of the License at
# *
# *      http://www.apache.org/licenses/LICENSE-2.0
# *
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
from datetime import datetime

import numpy as np
from cytomine import CytomineJob
from cytomine.models import ImageInstanceCollection, TermCollection, AnnotationCollection, UserCollection, \
    JobCollection, JobData


def write_csv(details, terms):
    lines = list()
    term_names = [t.name for t in terms]
    header = "Image,{},Total".format(','.join(term_names))

    def _write_matrix(kind):
        matrix = list()
        for name, image_details in details.items():
            line = "{},".format(name)
            for tn in term_names:
                line += "{},".format(image_details[tn][kind])
            line += "{}".format(image_details[kind])
            matrix.append(line)

        if kind != "ratio":
            line = "Total,"
            for tn in term_names:
                line += "{},".format(np.sum([image_details[tn][kind] for image_details in details.values()]))
            line += "{}".format(np.sum([image_details[kind] for image_details in details.values()]))
            matrix.append(line)
        matrix.append("")
        return matrix

    lines.append("Total annotation area per image and per term")
    lines.append(header)
    lines += _write_matrix("total")

    lines.append("Annotation count per image and per term")
    lines.append(header)
    lines += _write_matrix("count")

    lines.append("Area ratio per image and per term")
    lines.append(header)
    lines += _write_matrix("ratio")

    lines.append("*" * 30)
    lines.append("Details")
    lines.append("*" * 30)
    i = 1
    for name, image_details in details.items():
        lines.append("*" * 15)
        lines.append("Image {}".format(i))
        lines.append(name)

        for tn in term_names:
            lines.append(tn)
            lines.append("Created,Area")
            for a in image_details[tn]['annotations']:
                lines.append("{},{}".format(datetime.fromtimestamp(a['created'] / 1000), a['area']))

            lines.append("")
            lines.append("Annotation count, {}".format(image_details[tn]['count']))
            lines.append("Annotation average area, {}".format(image_details[tn]['mean']))
            lines.append("Annotation total area, {}".format(image_details[tn]['total']))
            lines.append("")

        lines.append("")
        lines.append("")
        i = i + 1

    n_commas = 1
    for l in lines:
        n_commas = max(n_commas, l.count(","))

    for i, l in enumerate(lines):
        count = l.count(",")
        if count < n_commas:
            lines[i] = l + "," * (n_commas - count)

    return lines


def main(argv):
    with CytomineJob.from_cli(argv) as cj:
        cj.job.update(progress=1, statusComment="Initialisation")
        cj.log(str(cj.parameters))

        term_ids = [int(term_id) for term_id in cj.parameters.cytomine_id_terms.split(",")]
        terms = TermCollection().fetch_with_filter("project", cj.parameters.cytomine_id_project)
        terms = [term for term in terms if term.id in term_ids]

        image_ids = [int(image_id) for image_id in cj.parameters.cytomine_id_images.split(",")]
        images = ImageInstanceCollection(light=True).fetch_with_filter("project", cj.parameters.cytomine_id_project)
        images = [image for image in images if image.id in image_ids]

        if hasattr(cj.parameters, "cytomine_id_users") and cj.parameters.cytomine_id_users is not None:
            user_ids = [int(user_id) for user_id in cj.parameters.cytomine_id_users.split(",")]
        else:
            user_ids = []

        if hasattr(cj.parameters, "cytomine_id_jobs") and cj.parameters.cytomine_id_jobs is not None:
            job_ids = [int(job_id) for job_id in cj.parameters.cytomine_id_jobs.split(",")]
            jobs = JobCollection(project=cj.parameters.cytomine_id_project).fetch()
            jobs = [job for job in jobs if job.id in job_ids]
        else:
            jobs = []

        userjobs_ids = [job.userJob for job in jobs]
        all_user_ids = user_ids + userjobs_ids

        cj.job.update(progress=20, statusComment="Collect data")
        ac = AnnotationCollection()
        ac.terms = term_ids
        ac.images = image_ids
        ac.showMeta = True
        ac.showGIS = True
        ac.showTerm = True
        ac.reviewed = True if cj.parameters.cytomine_reviewed_only else None
        ac.users = all_user_ids if len(all_user_ids) > 0 else None
        ac.fetch()

        cj.job.update(progress=55, statusComment="Compute statistics")
        data = dict()
        for image in images:
            d = dict()
            areas = [a.area for a in ac if a.image == image.id]
            total_area = np.sum(areas)
            d['total'] = total_area
            d['count'] = len(areas)
            d['ratio'] = 1.0
            for term in terms:
                annotations = [a for a in ac if a.image == image.id and term.id in a.term]
                areas = [a.area for a in annotations]
                d[term.name] = dict()
                d[term.name]['total'] = np.sum(areas)
                d[term.name]['count'] = len(annotations)
                d[term.name]['ratio'] = d[term.name]['total'] / float(total_area) if total_area > 0 else 0
                d[term.name]['mean'] = np.mean(areas)
                d[term.name]['annotations'] = [{"created": a.created, "area": a.area} for a in annotations]
            data[image.instanceFilename] = d

        cj.job.update(progress=90, statusComment="Write CSV report")
        with open("stat-area.csv", "w") as f:
            for l in write_csv(data, terms):
                f.write("{}\n".format(l))

        job_data = JobData(id_job=cj.job.id, key="Area CSV report", filename="stat-area.csv")
        job_data = job_data.save()
        job_data.upload("stat-area.csv")
        
        cj.job.update(statusComment="Finished.", progress=100)


if __name__ == "__main__":
    import sys
    main(sys.argv[1:])
