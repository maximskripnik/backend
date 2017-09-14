package services

import java.sql.SQLException

import models.ListWithTotal
import models.dao._
import models.event.Event
import models.project.Project
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import services.event.{EventJobService, EventService}
import testutils.fixture.EventFixture
import testutils.generator.EventGenerator
import utils.errors.{BadRequestError, NotFoundError}
import utils.listmeta.ListMeta

import scala.concurrent.Future

/**
  * Test for event service.
  */
class EventServiceTest extends BaseServiceTest with EventGenerator with EventFixture {

  private case class TestFixture(
    eventDaoMock: EventDao,
    groupDao: GroupDao,
    projectDao: ProjectDao,
    eventProjectDao: EventProjectDao,
    eventJobService: EventJobService,
    answerDao: AnswerDao,
    service: EventService
  )

  private def getFixture = {
    val daoMock = mock[EventDao]
    val groupDao = mock[GroupDao]
    val projectDao = mock[ProjectDao]
    val eventProjectDao = mock[EventProjectDao]
    val eventJobService = mock[EventJobService]
    val answerDao = mock[AnswerDao]
    val service = new EventService(daoMock, groupDao, projectDao, eventProjectDao, eventJobService, answerDao, ec)
    TestFixture(daoMock, groupDao, projectDao, eventProjectDao, eventJobService, answerDao, service)
  }

  "getById" should {

    "return not found if event not found" in {
      forAll { (id: Long) =>
        val fixture = getFixture
        when(fixture.eventDaoMock.findById(id)).thenReturn(toFuture(None))
        val result = wait(fixture.service.getById(id).run)

        result mustBe 'left
        result.swap.toOption.get mustBe a[NotFoundError]

        verify(fixture.eventDaoMock, times(1)).findById(id)
        verifyNoMoreInteractions(fixture.eventDaoMock)
      }
    }

    "return event from db" in {
      forAll { (event: Event, id: Long) =>
        val fixture = getFixture
        when(fixture.eventDaoMock.findById(id)).thenReturn(toFuture(Some(event)))
        val result = wait(fixture.service.getById(id).run)

        result mustBe 'right
        result.toOption.get mustBe event

        verify(fixture.eventDaoMock, times(1)).findById(id)
        verifyNoMoreInteractions(fixture.eventDaoMock)
      }
    }
  }

  "list" should {
    "return list of events from db" in {
      forAll {
        (
          projectId: Option[Long],
          status: Option[Event.Status],
          events: Seq[Event],
        ) =>
          val fixture = getFixture
          when(
            fixture.eventDaoMock.getList(
              optId = any[Option[Long]],
              optStatus = eqTo(status),
              optProjectId = eqTo(projectId),
              optFormId = any[Option[Long]],
              optGroupFromIds = any[Option[Seq[Long]]],
              optUserId = any[Option[Long]],
            )(any[ListMeta]))
            .thenReturn(toFuture(ListWithTotal(events)))
          val result = wait(fixture.service.list(status, projectId)(ListMeta.default).run)

          result mustBe 'right
          result.toOption.get mustBe ListWithTotal(events)
      }
    }
  }

  "create" should {
    "return conflict if can't validate event" in {
      forAll { (event: Event) =>
        whenever(
          (event.start after event.end) ||
            event.notifications.map(x => (x.kind, x.recipient)).distinct.length != event.notifications.length) {
          val fixture = getFixture

          val result = wait(fixture.service.create(event).run)

          result mustBe 'left
          result.swap.toOption.get mustBe a[BadRequestError]
        }
      }
    }

    "create event in db" in {
      val event = Events(2)

      val fixture = getFixture
      when(fixture.eventDaoMock.create(event.copy(id = 0))).thenReturn(toFuture(event))
      when(fixture.eventJobService.createJobs(event)).thenReturn(toFuture(()))
      val result = wait(fixture.service.create(event.copy(id = 0)).run)

      result mustBe 'right
      result.toOption.get mustBe event
    }
  }

  "update" should {
    "return conflict if can't validate event" in {
      forAll { (event: Event) =>
        whenever(
          (event.start after event.end) ||
            event.notifications.map(x => (x.kind, x.recipient)).distinct.length != event.notifications.length) {
          val fixture = getFixture
          when(fixture.eventDaoMock.findById(event.id)).thenReturn(toFuture(Some(event)))
          when(fixture.eventDaoMock.update(any[Event])).thenReturn(Future.failed(new SQLException("", "2300")))
          val result = wait(fixture.service.update(event.copy(id = 0)).run)

          result mustBe 'left
          result.swap.toOption.get mustBe a[BadRequestError]
        }
      }
    }

    "return not found if event not found" in {
      forAll { (event: Event) =>
        val fixture = getFixture
        when(fixture.eventDaoMock.findById(event.id)).thenReturn(toFuture(None))
        val result = wait(fixture.service.update(event).run)

        result mustBe 'left
        result.swap.toOption.get mustBe a[NotFoundError]

        verify(fixture.eventDaoMock, times(1)).findById(event.id)
        verifyNoMoreInteractions(fixture.eventDaoMock)
      }
    }

    "update event in db" in {
      val event = Events(2)
      val fixture = getFixture
      when(fixture.eventDaoMock.findById(event.id)).thenReturn(toFuture(Some(event)))
      when(fixture.eventDaoMock.update(event)).thenReturn(toFuture(event))
      when(fixture.eventJobService.createJobs(event)).thenReturn(toFuture(()))
      val result = wait(fixture.service.update(event).run)

      result mustBe 'right
      result.toOption.get mustBe event
    }
  }

  "delete" should {
    "return not found if event not found" in {
      forAll { (id: Long) =>
        val fixture = getFixture
        when(fixture.eventDaoMock.findById(id)).thenReturn(toFuture(None))
        val result = wait(fixture.service.delete(id).run)

        result mustBe 'left
        result.swap.toOption.get mustBe a[NotFoundError]

        verify(fixture.eventDaoMock, times(1)).findById(id)
        verifyNoMoreInteractions(fixture.eventDaoMock)
      }
    }

    "delete event from db" in {
      forAll { (id: Long) =>
        val fixture = getFixture
        when(fixture.eventDaoMock.findById(id)).thenReturn(toFuture(Some(Events(0))))
        when(fixture.eventDaoMock.delete(id)).thenReturn(toFuture(1))

        val result = wait(fixture.service.delete(id).run)

        result mustBe 'right
      }
    }
  }

  "cloneEvent" should {
    "return not found if event not found" in {
      forAll { (eventId: Long) =>
        val fixture = getFixture
        when(fixture.eventDaoMock.findById(eventId)).thenReturn(toFuture(None))
        val result = wait(fixture.service.cloneEvent(eventId).run)

        result mustBe 'left
        result.swap.toOption.get mustBe a[NotFoundError]
      }
    }

    "clone event" in {
      val event = Events(1)
      val createdEvent = event.copy(id = 2)

      val fixture = getFixture
      when(fixture.eventDaoMock.findById(event.id)).thenReturn(toFuture(Some(event)))
      when(fixture.eventDaoMock.create(any[Event])).thenReturn(toFuture(createdEvent))
      when(
        fixture.projectDao.getList(
          optId = any[Option[Long]],
          optEventId = eqTo(Some(createdEvent.id)),
          optGroupFromIds = any[Option[Seq[Long]]],
          optFormId = any[Option[Long]],
          optGroupAuditorId = any[Option[Long]],
          optEmailTemplateId = any[Option[Long]],
          optAnyRelatedGroupId = any[Option[Long]]
        )(any[ListMeta])).thenReturn(toFuture(ListWithTotal[Project](0, Nil)))
      when(fixture.eventJobService.createJobs(createdEvent)).thenReturn(toFuture(()))

      val result = wait(fixture.service.cloneEvent(event.id).run)

      result mustBe 'right
      result.toOption.get mustBe createdEvent
    }
  }
}
