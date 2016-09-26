from collections import namedtuple
import json
import logging

from airflow.bin.cli import trigger_dag
from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG
from datetime import datetime, timedelta

from rf.uploads.sentinel2 import find_sentinel2_scenes

rf_logger = logging.getLogger('rf')
ch = logging.StreamHandler()
ch.setLevel(logging.DEBUG)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
rf_logger.addHandler(ch)

logger = logging.getLogger(__name__)


seven_days_ago = datetime.combine(
    datetime.today() - timedelta(7), datetime.min.time()
)


args = {
    'owner': 'raster-foundry',
    'start_date': seven_days_ago,
}


dag = DAG(
    dag_id='find_sentinel2_scenes',
    default_args=args,
    schedule_interval=None
)


DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id')


def find_new_sentinel2_scenes(*args, **kwargs):
    """Fine new Sentinel 2 scenes and kick off imports"""
    logging.info("Finding Scenes...")

    tilepaths = find_sentinel2_scenes(2016, 9, 25)

    dag_id = 'import_sentinel2_scenes'

    logger.info('Kicking off %s dags to import scenes', len(tilepaths))
    for path in tilepaths:
        slug_path = '_'.join(path.split('/'))
        run_id = 'run_scene_import_{}'.format(slug_path)
        logger.info('Kicking of new scene import: %s', run_id)
        conf = json.dumps({'tilepath': path})
        dag_args = DagArgs(dag_id=dag_id, conf=conf, run_id=run_id)
        trigger_dag(dag_args)
    return "Finished kicking off new dags"


PythonOperator(
    task_id='find_new_sentinel2_scenes',
    provide_context=True,
    python_callable=find_new_sentinel2_scenes,
    dag=dag
)